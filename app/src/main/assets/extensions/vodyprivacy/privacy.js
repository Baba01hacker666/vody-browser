// Vody Privacy Controls — manual, user-defined anti-fingerprinting enforcement.
// The app pushes the current PrivacyConfig as a CustomEvent('vody-privacy', {detail: json}).
// We apply it in the content-script (isolated) world and patch the page-facing APIs.
(function () {
  if (window.__vodyPrivacyInstalled) return;
  window.__vodyPrivacyInstalled = true;

  var CFG = null;
  function apply(cfg) {
    CFG = cfg;
    if (!cfg) return;

    // --- WebGL vendor/renderer spoof ---
    if (cfg.spoofWebGL) {
      var VENDOR = "Vody Spoofed GPU";
      var RENDERER = "Vody Software Renderer";
      var wrap = function (proto, name, value) {
        try { Object.defineProperty(proto, name, { get: function () { return value; }, configurable: true }); } catch (e) {}
      };
      var origGetParam = WebGLRenderingContext.prototype.getParameter;
      var origGetParam2 = WebGL2RenderingContext.prototype.getParameter;
      var spoofParam = function (orig) {
        return function (p) {
          try {
            var gl = this;
            var dbg = null;
            try { dbg = gl.getExtension("WEBGL_debug_renderer_info"); } catch (e) {}
            if (dbg) {
              if (p === dbg.UNMASKED_VENDOR_WEBGL) return VENDOR;
              if (p === dbg.UNMASKED_RENDERER_WEBGL) return RENDERER;
            }
          } catch (e) {}
          return orig.call(this, p);
        };
      };
      WebGLRenderingContext.prototype.getParameter = spoofParam(origGetParam);
      WebGL2RenderingContext.prototype.getParameter = spoofParam(origGetParam2);
    }

    // --- Timezone spoof ---
    if (cfg.spoofTimezone && cfg.customTimezone) {
      try {
        var tz = cfg.customTimezone;
        var makeTZ = function (orig) {
          return function (t, n) { return tz; };
        };
        try { Intl.DateTimeFormat.prototype.resolvedOptions = (function (orig) {
          return function () { var o = orig.call(this); o.timeZone = tz; return o; };
        })(Intl.DateTimeFormat.prototype.resolvedOptions); } catch (e) {}
        var origToString = Date.prototype.toString;
        Date.prototype.toString = function () {
          var s = origToString.call(this);
          // rewrite the "(TIMEZONE)" token Firefox appends
          return s.replace(/\(([^)]+)\)$/, "(" + tz + ")");
        };
        var origGetTZOffset = Date.prototype.getTimezoneOffset;
        // approximate offset from the chosen zone for "now"
        var getOffset = function (date) {
          try {
            var dtf = new Intl.DateTimeFormat("en-US", { timeZone: tz, timeZoneName: "shortOffset" });
            var parts = dtf.formatToParts(date);
            var off = null;
            for (var i = 0; i < parts.length; i++) if (parts[i].type === "timeZoneName") off = parts[i].value;
            if (off) {
              var m = /GMT([+-]\d{2}):?(\d{2})?/.exec(off);
              if (m) {
                var h = parseInt(m[1], 10), mm = parseInt(m[2] || "0", 10);
                return -(h * 60 + mm) * (m[1][0] === "-" ? 1 : -1);
              }
            }
          } catch (e) {}
          return origGetTZOffset.call(date);
        };
        Date.prototype.getTimezoneOffset = function () { return getOffset(this); };
      } catch (e) {}
    }

    // --- Fonts spoof ---
    if (cfg.spoofFonts && cfg.customFonts) {
      try {
        var fonts = cfg.customFonts;
        var cs = window.getComputedStyle;
        window.getComputedStyle = function (el, pseudo) {
          var s = cs.call(this, el, pseudo);
          try {
            Object.defineProperty(s, "fontFamily", { get: function () { return fonts; }, configurable: true });
          } catch (e) {}
          return s;
        };
        // also report only the chosen families from measureText-based probing is not feasible;
        // patching CSS.supports font-family is enough to keep a consistent surface.
        if (window.CSS && CSS.supports) {
          var origSupports = CSS.supports.bind(CSS);
          CSS.supports = function (prop, val) {
            if (/font-family/i.test(prop)) return true;
            return origSupports(prop, val);
          };
        }
      } catch (e) {}
    }

    // --- Username / identity token ---
    if (cfg.spoofUsername && cfg.customUsername) {
      try {
        Object.defineProperty(navigator, "identity", { get: function () { return { username: cfg.customUsername }; }, configurable: true });
        Object.defineProperty(navigator, "vendor", { get: function () { return cfg.customUsername; }, configurable: true });
      } catch (e) {
        try { navigator.__vodyUsername = cfg.customUsername; } catch (e2) {}
      }
    }

    // --- Arbitrary API responses (user-defined) ---
    if (cfg.spoofApis && cfg.apiResponses) {
      try {
        var api = JSON.parse(cfg.apiResponses);
        for (var key in api) {
          if (!api.hasOwnProperty(key)) continue;
          var path = key.split(".");
          if (path[0] === "navigator" && path.length === 2) {
            try { Object.defineProperty(navigator, path[1], { get: function () { return api[key]; }, configurable: true }); } catch (e) {}
          } else if (path[0] === "screen" && path.length === 2 && window.screen) {
            try { Object.defineProperty(window.screen, path[1], { get: function () { return api[key]; }, configurable: true }); } catch (e) {}
          }
        }
      } catch (e) {}
    }
  }

  window.addEventListener("vody-privacy", function (e) {
    try { apply(JSON.parse(e.detail)); } catch (err) {}
  });

  // Ask the app for the current config as soon as we load.
  try { browser.runtime.sendMessage({ type: "vody-privacy-request" }); } catch (e) {}
})();
