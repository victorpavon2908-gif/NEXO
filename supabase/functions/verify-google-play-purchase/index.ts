import { createClient } from "https://esm.sh/@supabase/supabase-js@2.57.4";

const PACKAGE_NAME = "ni.nexo.app";
const SUBSCRIPTIONS = new Set(["nexo_plus_monthly", "nexo_plus_yearly"]);
const ONE_TIME = "nexo_boost_24h";

const corsHeaders = {
  "Content-Type": "application/json",
};

Deno.serve(async (request) => {
  if (request.method !== "POST") return json({ verified: false, error: "method_not_allowed" }, 405);

  try {
    const authorization = request.headers.get("Authorization") ?? "";
    const supabaseUrl = requiredEnv("SUPABASE_URL");
    const anonKey = requiredEnv("SUPABASE_ANON_KEY");
    const serviceKey = requiredEnv("SUPABASE_SERVICE_ROLE_KEY");
    const userClient = createClient(supabaseUrl, anonKey, { global: { headers: { Authorization: authorization } } });
    const { data: userData, error: userError } = await userClient.auth.getUser();
    if (userError || !userData.user) return json({ verified: false, error: "unauthorized" }, 401);

    const body = await request.json();
    if (body.health_check === true) {
      const ready = Boolean(
        Deno.env.get("GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL")?.trim() &&
        Deno.env.get("GOOGLE_PLAY_PRIVATE_KEY")?.trim()
      );
      return json({ verified: false, ready });
    }
    const purchaseToken = String(body.purchase_token ?? "").trim();
    const productIds = Array.isArray(body.product_ids) ? body.product_ids.map(String) : [];
    const productId = productIds.find((id: string) => SUBSCRIPTIONS.has(id) || id === ONE_TIME);
    if (!purchaseToken || !productId) return json({ verified: false, error: "invalid_purchase" }, 400);

    const tokenHash = await sha256(purchaseToken);
    const admin = createClient(supabaseUrl, serviceKey, { auth: { persistSession: false } });
    const { data: previous } = await admin.from("billing_purchase_audit")
      .select("user_id,product_id,expires_at").eq("purchase_token_hash", tokenHash).maybeSingle();
    if (previous) {
      if (previous.user_id !== userData.user.id) return json({ verified: false, error: "purchase_owner_mismatch" }, 409);
      // Los consumibles no vuelven a concederse. Las suscripciones sí se
      // revalidan porque Google puede renovar el mismo token con otra fecha.
      if (previous.product_id === ONE_TIME) return json({ verified: true, restored: true });
    }

    const googleToken = await googleAccessToken();
    let expiresAt: string | null = null;
    let purchaseType = "one_time";

    if (SUBSCRIPTIONS.has(productId)) {
      purchaseType = "subscription";
      const url = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PACKAGE_NAME}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`;
      const result = await googleJson(url, googleToken);
      const allowedStates = new Set([
        "SUBSCRIPTION_STATE_ACTIVE",
        "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
        "SUBSCRIPTION_STATE_CANCELED",
      ]);
      const line = result.lineItems?.find((item: { productId?: string }) => item.productId === productId);
      expiresAt = line?.expiryTime ?? null;
      if (!allowedStates.has(result.subscriptionState) || !expiresAt || Date.parse(expiresAt) <= Date.now()) {
        return json({ verified: false, error: "subscription_inactive" }, 400);
      }
    } else {
      const url = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PACKAGE_NAME}/purchases/products/${productId}/tokens/${encodeURIComponent(purchaseToken)}`;
      const result = await googleJson(url, googleToken);
      if (result.purchaseState !== 0) return json({ verified: false, error: "purchase_inactive" }, 400);
      expiresAt = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString();
    }

    const { error: grantError } = await admin.rpc("grant_verified_google_play_purchase", {
      target_user_id: userData.user.id,
      token_hash: tokenHash,
      verified_product_id: productId,
      verified_purchase_type: purchaseType,
      verified_expires_at: expiresAt,
    });
    if (grantError) throw grantError;

    return json({ verified: true, expires_at: expiresAt });
  } catch (_error) {
    return json({ verified: false, error: "verification_failed" }, 500);
  }
});

async function googleAccessToken(): Promise<string> {
  const email = requiredEnv("GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL");
  const privateKey = requiredEnv("GOOGLE_PLAY_PRIVATE_KEY").replace(/\\n/g, "\n");
  const now = Math.floor(Date.now() / 1000);
  const header = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = base64Url(JSON.stringify({
    iss: email,
    scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const unsigned = `${header}.${claim}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemBytes(privateKey),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned));
  const assertion = `${unsigned}.${base64UrlBytes(new Uint8Array(signature))}`;
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }),
  });
  if (!response.ok) throw new Error("google_auth_failed");
  return (await response.json()).access_token;
}

async function googleJson(url: string, accessToken: string) {
  const response = await fetch(url, { headers: { Authorization: `Bearer ${accessToken}` } });
  if (!response.ok) throw new Error("google_verification_failed");
  return await response.json();
}

async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest)).map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

function pemBytes(pem: string): Uint8Array {
  const body = pem.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g, "");
  return Uint8Array.from(atob(body), (char) => char.charCodeAt(0));
}

function base64Url(value: string): string {
  return base64UrlBytes(new TextEncoder().encode(value));
}

function base64UrlBytes(bytes: Uint8Array): string {
  let binary = "";
  bytes.forEach((byte) => binary += String.fromCharCode(byte));
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function requiredEnv(name: string): string {
  const value = Deno.env.get(name)?.trim();
  if (!value) throw new Error(`missing_${name}`);
  return value;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders });
}
