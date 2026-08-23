package web;

/**
 * All HTML/CSS for the web UI — no frameworks, no build step.
 *
 * Design language: a navigation chart. The product computes a *bearing*
 * (which domain) and a *distance* (how far your skills sit from it), so the
 * interface borrows chart and instrument conventions: pale chart paper with a
 * graticule, deep ink for the panel, magenta for markers (the colour real
 * charts reserve for aids to navigation), and monospaced readouts for every
 * number the engine produces.
 */
public final class Pages {

    private Pages() {}

    public static final String FONTS =
            "<link rel=\"preconnect\" href=\"https://fonts.googleapis.com\">"
          + "<link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin>"
          + "<link href=\"https://fonts.googleapis.com/css2?"
          + "family=Archivo:wght@500;600;700;800&"
          + "family=IBM+Plex+Sans:wght@400;500;600&"
          + "family=IBM+Plex+Mono:wght@400;500;600&display=swap\" rel=\"stylesheet\">";

    public static final String CSS = """
:root{
  --abyss:#0B1F2A; --abyss-2:#143543; --chart:#E7EDEA; --surface:#FFFFFF;
  --marker:#C2185B; --marker-soft:#FBE7EF; --depth:#0E7C7B; --depth-soft:#DFF0EE;
  --sand:#B8860B; --sand-soft:#F7EDD4; --slate:#59707D; --line:#CBD8D4;
  --display:'Archivo','Helvetica Neue',Helvetica,Arial,sans-serif;
  --body:'IBM Plex Sans',system-ui,-apple-system,'Segoe UI',sans-serif;
  --mono:'IBM Plex Mono','SF Mono',Consolas,monospace;
}
*{box-sizing:border-box;margin:0;padding:0}
html{-webkit-text-size-adjust:100%}
body{
  font-family:var(--body);color:var(--abyss);line-height:1.6;
  background-color:var(--chart);
  background-image:
    linear-gradient(var(--line) 1px,transparent 1px),
    linear-gradient(90deg,var(--line) 1px,transparent 1px);
  background-size:56px 56px,56px 56px;
  background-position:-1px -1px;
}
a{color:var(--marker);text-decoration:none;border-bottom:1px solid rgba(194,24,91,.3)}
a:hover{border-bottom-color:var(--marker)}
:focus-visible{outline:2px solid var(--marker);outline-offset:2px}

/* ---------- panel / topbar ---------- */
.topbar{position:relative;z-index:2;background:var(--abyss);color:#fff;
  padding:.85rem 1.5rem;display:flex;align-items:center;gap:.75rem;flex-wrap:wrap;
  border-bottom:3px solid var(--marker)}
.topbar .mark{width:26px;height:26px;flex:none}
.topbar .brand{font-family:var(--display);font-weight:800;font-size:1.05rem;
  letter-spacing:.09em;text-transform:uppercase}
.topbar .spacer{flex:1}
.topbar a{color:#BFD3D0;font-size:.82rem;margin-left:1.3rem;border:none;
  font-family:var(--mono);letter-spacing:.03em}
.topbar a:hover{color:#fff;border:none}

.wrap{position:relative;z-index:1;max-width:960px;margin:2.4rem auto 4rem;padding:0 1.25rem}

/* ---------- type ---------- */
h1{font-family:var(--display);font-weight:800;font-size:clamp(1.6rem,4vw,2.15rem);
  letter-spacing:-.02em;line-height:1.15;margin-bottom:.4rem}
h2{font-family:var(--display);font-weight:700;font-size:1.05rem;
  letter-spacing:.01em;margin:1.7rem 0 .7rem}
.eyebrow{font-family:var(--mono);font-size:.7rem;font-weight:500;
  letter-spacing:.18em;text-transform:uppercase;color:var(--slate);
  display:block;margin-bottom:.5rem}
.sub{color:var(--slate);font-size:.95rem;margin-bottom:1.6rem;max-width:62ch}

/* ---------- cards ---------- */
.card{background:var(--surface);border:1px solid var(--line);
  padding:1.35rem 1.5rem;margin-bottom:1rem;
  box-shadow:0 1px 0 rgba(11,31,42,.04)}
.card.narrow{max-width:420px;margin:3.5rem auto}
.card.lead{border-left:3px solid var(--marker)}

/* ---------- forms ---------- */
label{display:block;font-family:var(--mono);font-size:.7rem;color:var(--slate);
  margin:1rem 0 .3rem;text-transform:uppercase;letter-spacing:.12em}
input,select{width:100%;padding:.6rem .75rem;border:1px solid var(--line);
  border-radius:0;font-size:1rem;font-family:var(--body);
  background:var(--surface);color:var(--abyss)}
input:focus,select:focus{outline:2px solid var(--marker);outline-offset:0;
  border-color:var(--marker)}
button{margin-top:1.25rem;padding:.65rem 1.5rem;border:none;border-radius:0;
  background:var(--abyss);color:#fff;font-size:.85rem;cursor:pointer;
  font-family:var(--mono);letter-spacing:.09em;text-transform:uppercase;
  transition:background .15s ease}
button:hover{background:var(--marker)}
button.brass{background:var(--marker)}
button.brass:hover{background:#9E1349}
button.small{margin:0;padding:.35rem .85rem;font-size:.68rem}
button.danger{background:#9E1349}

/* ---------- messages ---------- */
.note{background:var(--sand-soft);border-left:3px solid var(--sand);
  padding:.8rem 1rem;font-size:.9rem;margin-bottom:1.1rem}
.note code{font-family:var(--mono);font-weight:600;font-size:1.15rem;
  letter-spacing:.22em;color:var(--abyss)}
.error{background:var(--marker-soft);border-left:3px solid var(--marker);
  padding:.8rem 1rem;font-size:.9rem;margin-bottom:1.1rem;color:#8E1140}

/* ---------- the bearing tape (signature element) ---------- */
.gauge{margin:.2rem 0 .1rem}
.gauge .head{display:flex;justify-content:space-between;align-items:baseline;gap:1rem}
.gauge .name{font-family:var(--display);font-weight:700;font-size:1.12rem;
  letter-spacing:-.01em}
.gauge .deg{font-family:var(--mono);font-weight:600;font-size:1.35rem;
  color:var(--marker);font-variant-numeric:tabular-nums}
.gauge .track{height:26px;margin-top:.6rem;position:relative;
  background:
    repeating-linear-gradient(90deg,
      var(--line) 0,var(--line) 1px,transparent 1px,transparent 10%);
  border-bottom:2px solid var(--abyss)}
.gauge .fill{position:absolute;left:0;bottom:0;height:8px;
  background:var(--depth-soft);border-bottom:2px solid var(--depth)}
.gauge .needle{position:absolute;bottom:-2px;width:2px;height:26px;
  background:var(--marker)}
.gauge .needle::before{content:"";position:absolute;top:-5px;left:-4px;
  width:0;height:0;border-left:5px solid transparent;
  border-right:5px solid transparent;border-top:6px solid var(--marker)}
.gauge .split{font-family:var(--mono);font-size:.72rem;color:var(--slate);
  margin-top:.5rem;letter-spacing:.05em}
.rank{font-family:var(--mono);font-size:.7rem;color:var(--slate);
  letter-spacing:.15em;display:block;margin-bottom:.4rem}

.reasons{font-size:.88rem;color:var(--slate);margin:.75rem 0 0;list-style:none}
.reasons li{position:relative;padding-left:1.1rem;margin-bottom:.25rem}
.reasons li::before{content:"\\2192";position:absolute;left:0;color:var(--depth)}

/* ---------- chips ---------- */
.chips{margin:.5rem 0}
.chip{display:inline-block;padding:.22rem .65rem;font-size:.76rem;
  font-family:var(--mono);margin:.2rem .3rem .2rem 0;
  border:1px solid var(--line);background:var(--surface);color:var(--slate)}
.chip.have{background:var(--depth-soft);border-color:var(--depth);color:#0A5F5E}
.chip.need{background:var(--marker-soft);border-color:var(--marker);color:#8E1140}

/* ---------- tables ---------- */
table{width:100%;border-collapse:collapse;font-size:.9rem}
th{font-family:var(--mono);color:var(--slate);text-transform:uppercase;
  font-size:.68rem;letter-spacing:.12em;text-align:left;padding:.5rem .65rem;
  border-bottom:2px solid var(--abyss)}
td{padding:.65rem;border-bottom:1px solid var(--line);vertical-align:top}
tbody tr:hover{background:var(--chart)}
.badge{font-family:var(--mono);font-size:.68rem;padding:.2rem .55rem;
  letter-spacing:.08em;text-transform:uppercase;font-weight:600}
.badge.ready{background:var(--depth-soft);color:#0A5F5E;border:1px solid var(--depth)}
.badge.upskill{background:var(--sand-soft);color:#6B4E06;border:1px solid var(--sand)}
.badge.role{background:var(--chart);color:var(--slate);border:1px solid var(--line)}
.badge.danger-badge{background:var(--marker-soft);color:#8E1140;border:1px solid var(--marker)}

/* ---------- layout helpers ---------- */
.grid2{display:grid;grid-template-columns:1fr 1fr;gap:1rem}
.grid5{display:grid;grid-template-columns:repeat(5,1fr);gap:.75rem}
.stat{background:var(--abyss);color:#fff;padding:1rem;text-align:left}
.stat b{display:block;font-family:var(--mono);font-weight:600;font-size:1.9rem;
  color:#fff;font-variant-numeric:tabular-nums;line-height:1.1}
.stat span{font-family:var(--mono);font-size:.66rem;color:#8FA9AE;
  text-transform:uppercase;letter-spacing:.14em}
.filters{display:flex;gap:.9rem;align-items:flex-end;flex-wrap:wrap}
.filters div{flex:1;min-width:150px}
.filters label{margin-top:0}
.filters button{margin-top:0}

/* ---------- landing hero ---------- */
.hero{padding:2.6rem 0 1rem}
.hero h1{max-width:16ch;font-size:clamp(2rem,5vw,2.9rem)}
.hero .lede{font-size:1.05rem;max-width:58ch;margin:1rem 0 1.7rem}
.hero .cta-row{display:flex;gap:.9rem;flex-wrap:wrap;margin-bottom:1.9rem}
.btn{display:inline-block;padding:.7rem 1.6rem;border:none;background:var(--abyss);
  color:#fff!important;font-size:.85rem;cursor:pointer;font-family:var(--mono);
  letter-spacing:.09em;text-transform:uppercase;transition:background .15s ease;
  border-bottom:none!important}
.btn:hover{background:var(--marker);border-bottom:none}
.btn.brass{background:var(--marker)}
.btn.brass:hover{background:#9E1349}
.btn.ghost{background:transparent;color:var(--abyss)!important;border:1px solid var(--abyss)}
.btn.ghost:hover{background:var(--abyss);color:#fff!important}
.tech-strip{margin:0 0 2rem}
.tech-strip .chip{background:var(--surface)}

.steps{display:grid;grid-template-columns:repeat(3,1fr);gap:1rem;margin:0 0 2.2rem}
.steps .step{background:var(--surface);border:1px solid var(--line);padding:1.2rem 1.3rem}
.steps .step .num{font-family:var(--mono);color:var(--marker);font-weight:700;
  font-size:1.3rem;display:block;margin-bottom:.4rem}
.steps .step h3{font-family:var(--display);font-size:.95rem;margin-bottom:.35rem}
.steps .step p{font-size:.85rem;color:var(--slate)}

.roles-grid{display:grid;grid-template-columns:repeat(5,1fr);gap:.75rem;margin:0 0 2.2rem}
.roles-grid .role{background:var(--surface);border:1px solid var(--line);
  padding:.9rem .8rem;text-align:left}
.roles-grid .role b{display:block;font-family:var(--display);font-size:.82rem;
  margin-bottom:.3rem}
.roles-grid .role span{font-size:.76rem;color:var(--slate)}

/* ---------- auth split layout ---------- */
.auth-shell{display:grid;grid-template-columns:1.05fr 1fr;gap:2.4rem;
  align-items:start;margin:2.2rem auto;max-width:920px}
.auth-shell .card.narrow{margin:0;max-width:none}
.auth-aside{background:var(--abyss);color:#fff;padding:2rem 2.1rem;
  position:relative;overflow:hidden;
  background-image:
    linear-gradient(rgba(255,255,255,.05) 1px,transparent 1px),
    linear-gradient(90deg,rgba(255,255,255,.05) 1px,transparent 1px);
  background-size:28px 28px}
.auth-aside::before,.auth-aside::after{content:"";position:absolute;
  right:-30px;bottom:-30px;border-radius:50%;pointer-events:none}
.auth-aside::before{width:230px;height:230px;border:1px solid rgba(255,255,255,.08)}
.auth-aside::after{width:150px;height:150px;right:10px;bottom:10px;
  border:1px solid rgba(194,24,91,.3)}
.auth-aside .eyebrow{color:#8FA9AE}
.auth-aside .pitch{font-family:var(--display);font-weight:800;
  font-size:1.35rem;line-height:1.28;margin-bottom:.9rem;position:relative}
.auth-aside .lede{color:#BFD3D0;font-size:.88rem;margin-bottom:1.5rem;
  max-width:38ch;position:relative}
.auth-aside .role-list{list-style:none;margin-bottom:1.6rem;position:relative}
.auth-aside .role-list li{position:relative;padding-left:1.2rem;
  margin-bottom:.5rem;font-size:.85rem;color:#DCE6E4}
.auth-aside .role-list li::before{content:"\\2192";position:absolute;left:0;
  color:var(--marker)}
.auth-aside .demo-note{font-family:var(--mono);font-size:.72rem;
  color:#8FA9AE;letter-spacing:.08em;text-transform:uppercase;
  margin-bottom:.5rem;position:relative}
.auth-aside table{font-size:.78rem;position:relative}
.auth-aside th{color:#8FA9AE;border-bottom:1px solid rgba(255,255,255,.18)}
.auth-aside td{border-bottom:1px solid rgba(255,255,255,.1);color:#DCE6E4}
.auth-aside tbody tr:hover{background:rgba(255,255,255,.04)}

@media(max-width:820px){
  .steps,.roles-grid{grid-template-columns:1fr 1fr}
  .auth-shell{grid-template-columns:1fr}
}
@media(max-width:640px){
  .grid2,.grid5{grid-template-columns:1fr}
  .steps,.roles-grid{grid-template-columns:1fr}
  .wrap{margin-top:1.5rem}
  .topbar{padding:.75rem 1rem}
  .topbar a{margin-left:0;margin-right:1rem}
  table{font-size:.82rem}
}
@media(prefers-reduced-motion:reduce){*{transition:none!important}}
""";

    /** Top-nav shown when nobody is signed in (landing/login/register/verify). */
    public static final String LOGGED_OUT_NAV =
            "<a href=\"/\">Home</a><a href=\"/login\">Sign in</a><a href=\"/register\">Create account</a>";

    /** Inline SVG compass-rose mark used in the top bar. */
    public static final String MARK = """
<svg class="mark" viewBox="0 0 24 24" fill="none" aria-hidden="true">
<circle cx="12" cy="12" r="10.5" stroke="#BFD3D0" stroke-width="1.2"/>
<path d="M12 3 L13.5 10.5 L21 12 L13.5 13.5 L12 21 L10.5 13.5 L3 12 L10.5 10.5 Z"
 fill="#C2185B"/><circle cx="12" cy="12" r="1.5" fill="#0B1F2A"/></svg>""";

    public static String shell(String title, String nav, String body) {
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + title + " \u00B7 CareerCompass</title>"
                + FONTS
                + "<link rel=\"stylesheet\" href=\"/style.css\"></head><body>"
                + "<header class=\"topbar\">" + MARK
                + "<span class=\"brand\">CareerCompass</span><span class=\"spacer\"></span>"
                + nav + "</header><main class=\"wrap\">" + body + "</main></body></html>";
    }

    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    public static String errorBox(String msg) {
        return msg == null || msg.isBlank() ? "" : "<div class=\"error\">" + esc(msg) + "</div>";
    }

    public static String noteBox(String msg) {
        return msg == null || msg.isBlank() ? "" : "<div class=\"note\">" + msg + "</div>";
    }

    /** A single number-and-label tile, e.g. the counters atop Trends/Admin/Recruiter pages. */
    public static String stat(Object value, String label) {
        return "<div class=\"stat\"><b>" + value + "</b><span>" + label + "</span></div>";
    }

    /**
     * The bearing-tape gauge — the signature element shared by recommendation
     * scores, domain-popularity bars, and the internship funnel. {@code name}
     * and {@code splitLine} must already be escaped by the caller if needed;
     * {@code splitLine} may be null/blank to omit the secondary readout.
     */
    public static String gauge(String name, String deg, int pct, String splitLine) {
        String split = (splitLine == null || splitLine.isBlank())
                ? "" : "<div class=\"split\">" + splitLine + "</div>";
        return "<div class=\"gauge\"><div class=\"head\"><span class=\"name\">" + name
                + "</span><span class=\"deg\">" + deg + "</span></div>"
                + "<div class=\"track\"><div class=\"fill\" style=\"width:" + pct + "%\"></div>"
                + "<div class=\"needle\" style=\"left:" + pct + "%\"></div></div>" + split + "</div>";
    }

    /** Status pill for an application or mentorship-request row. */
    public static String statusBadge(String status) {
        String cls = switch (status) {
            case "OFFER" -> "ready";
            case "REJECTED" -> "danger-badge";
            case "APPLIED" -> "role";
            default -> "upskill";
        };
        return "<span class=\"badge " + cls + "\">" + esc(status) + "</span>";
    }

    /** Truncates an ISO-8601 instant down to its date portion for table display. */
    public static String shortDate(String iso) {
        try { return iso.substring(0, 10); } catch (Exception e) { return iso; }
    }
}
