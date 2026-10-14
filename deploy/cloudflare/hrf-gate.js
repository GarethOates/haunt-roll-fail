// hrf-gate: a Cloudflare Worker on hrf.botc.games/* that asks for a shared code before anything reaches the site.
// The right code sets a signed cookie (HMAC of its expiry, HttpOnly, 90 days); with it every request passes through
// to the origin unchanged. Secrets (Worker settings, never in this file): HRF_CODE, the code; SIGNING_KEY, the
// cookie key (changing it logs everyone out). Deployed by deploy/cloudflare/deploy-gate.py.

const COOKIE = "hrf_gate";
const LOGIN = "/__hrf_login";
const DAYS = 90;
const WRONG_DELAY_MS = 2000;   // slows down guessing; the zone's rate limiting rule on LOGIN does the rest

const enc = new TextEncoder();

async function sign(key, message) {
    const k = await crypto.subtle.importKey("raw", enc.encode(key), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
    const sig = new Uint8Array(await crypto.subtle.sign("HMAC", k, enc.encode(message)));
    return btoa(String.fromCharCode(...sig)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function same(a, b) {
    if (a.length !== b.length)
        return false;
    let r = 0;
    for (let i = 0; i < a.length; i++)
        r |= a.charCodeAt(i) ^ b.charCodeAt(i);
    return r === 0;
}

function cookieValue(request) {
    const m = (request.headers.get("Cookie") || "").match(/(?:^|;\s*)hrf_gate=([^;]+)/);
    return m ? m[1] : null;
}

async function signedIn(request, env) {
    const v = cookieValue(request);
    if (!v)
        return false;
    const [expiry, sig] = v.split(".");
    if (!expiry || !sig || !(+expiry > Date.now() / 1000))
        return false;
    return same(sig, await sign(env.SIGNING_KEY, expiry));
}

// only paths of this site: no scheme, no host, no protocol-relative "//"
function safeNext(next) {
    return typeof next === "string" && next.startsWith("/") && !next.startsWith("//") && !next.startsWith(LOGIN) ? next : "/play";
}

function escape(s) {
    return s.replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}

function loginPage(next, wrong, status) {
    const html = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>HRF</title>
<style>
    :root { color-scheme: dark; }
    body { margin: 0; min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #1b1b1b; color: #d3d3d3; font: 18px/1.4 Georgia, serif; }
    form { background: #232323; border: 1px solid #444; border-radius: 10px; padding: 32px 28px; width: min(320px, calc(100vw - 32px)); box-sizing: border-box; text-align: center; }
    h1 { margin: 0 0 6px; font-size: 26px; font-weight: normal; letter-spacing: 1px; }
    p { margin: 0 0 20px; color: #999; font-size: 15px; }
    input { width: 100%; box-sizing: border-box; padding: 12px; font: 24px monospace; letter-spacing: 6px; text-align: center; color: #eee; background: #111; border: 1px solid #666; border-radius: 6px; }
    input:focus { outline: 2px solid #b08d57; border-color: #b08d57; }
    button { margin-top: 16px; width: 100%; padding: 11px; font: inherit; color: #eee; background: #111; border: 1px solid #999; border-radius: 6px; cursor: pointer; }
    button:hover { border-color: #ddd; }
    .wrong { color: #e0605a; margin: 12px 0 0; font-size: 15px; }
</style>
</head>
<body>
<form method="post" action="${LOGIN}">
    <h1>Haunt Roll Fail</h1>
    <p>Enter the code to play</p>
    <input name="code" type="password" inputmode="numeric" autocomplete="current-password" autofocus required aria-label="Code">
    <input name="next" type="hidden" value="${escape(next)}">
    <button type="submit">Enter</button>
    ${wrong ? '<div class="wrong">That code is not right</div>' : ""}
</form>
</body>
</html>`;
    return new Response(html, { status, headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store", "X-Robots-Tag": "noindex" } });
}

export default {
    async fetch(request, env) {
        const url = new URL(request.url);

        if (url.pathname === LOGIN) {
            if (request.method !== "POST")
                return loginPage(safeNext(url.searchParams.get("next")), false, 200);
            const form = await request.formData();
            const next = safeNext(form.get("next"));
            const code = String(form.get("code") || "").trim();
            if (!same(code, env.HRF_CODE)) {
                await new Promise(r => setTimeout(r, WRONG_DELAY_MS));
                return loginPage(next, true, 401);
            }
            const expiry = String(Math.floor(Date.now() / 1000) + DAYS * 86400);
            const value = expiry + "." + await sign(env.SIGNING_KEY, expiry);
            return new Response(null, { status: 303, headers: {
                "Location": next,
                "Set-Cookie": `${COOKIE}=${value}; Path=/; Max-Age=${DAYS * 86400}; HttpOnly; Secure; SameSite=Lax`,
                "Cache-Control": "no-store",
            } });
        }

        if (await signedIn(request, env))
            return fetch(request);

        // a page (a game link, the lobby): the login form, back to the same address after the code
        const page = request.method === "GET" && (request.headers.get("Accept") || "").includes("text/html");
        if (page)
            return loginPage(safeNext(url.pathname + url.search), false, 401);
        return new Response("Code required", { status: 401, headers: { "Content-Type": "text/plain", "Cache-Control": "no-store" } });
    },
};
