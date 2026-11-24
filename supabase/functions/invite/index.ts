// The household invite link: https://<project>.supabase.co/functions/v1/invite?c=CODE
//
// On Android it hands straight over to Opaw (opaw://join?code=CODE), or to the Play Store when Opaw
// isn't installed yet; the code is kept in the Play Store link so the app can still pick it up.
// Anywhere else it answers with the code in plain words, to type into Opaw.
// Deployed without JWT checks: whoever has the link can open it, and joining still needs sign-in.

const PACKAGE = "app.opaw";

Deno.serve((req) => {
  const url = new URL(req.url);
  const code = (url.searchParams.get("c") ?? "").toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 32);
  if (!code) return new Response("This invite link is missing its code.", { status: 400 });
  const store = `https://play.google.com/store/apps/details?id=${PACKAGE}&referrer=${encodeURIComponent(`invite=${code}`)}`;
  const agent = req.headers.get("user-agent") ?? "";
  if (/android/i.test(agent)) {
    const intent = `intent://join?code=${code}#Intent;scheme=opaw;package=${PACKAGE};S.browser_fallback_url=${encodeURIComponent(store)};end`;
    return new Response(null, { status: 302, headers: { Location: intent, "Cache-Control": "no-store" } });
  }
  return new Response(
    `You're invited to help look after a pet on Opaw.\n\nInstall Opaw, sign in, and enter this invite code: ${code}\n\nAndroid: ${store}\n`,
    { headers: { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "no-store" } },
  );
});
