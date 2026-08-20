// Vody built-in DevTools eval bridge.
// Runs in the content-script (isolated) world. The app triggers evaluation by
// dispatching a CustomEvent with the JS source; we eval it here (where the
// browser.runtime messaging API is available) and post the result back.
(function () {
  if (window.__vodyEvalInstalled) return;
  window.__vodyEvalInstalled = true;

  function post(msg) {
    try { browser.runtime.sendMessage(msg); } catch (e) {}
  }

  window.addEventListener("vody-eval", function (e) {
    var src = e.detail;
    try {
      var r = eval(src);
      post({ type: "eval", ok: true, result: (r && typeof r === "object") ? JSON.stringify(r) : String(r) });
    } catch (err) {
      post({ type: "eval", ok: false, error: String(err) });
    }
  });

  post({ type: "ready" });
})();
