package com.bb.bot.handler.news.render;

/**
 * 每日资讯日报 HTML 模板片段（CSS / JS / 页面骨架）。
 *
 * <p>把固定的样式表与脚本以 Java 文本块的形式集中在此，{@link NewsPageBuilderImpl}
 * 负责把动态内容（卡片、Tab、速览、往期导航）拼进这些骨架。样式与脚本照搬已验收的
 * demo（分类 Tab 筛选 + 站内搜索 + 跨天往期导航 + 响应式 + 跟随系统深色模式），
 * 不引任何外部框架 / CDN，产出自包含 HTML。</p>
 *
 * <p>注意：文本块里出现的 {@code $} 不在此文件做插值，均为字面量（脚本里的模板字符串
 * 用 {@code ${...}}）；HTML 转义统一由 {@link #escape(String)} 处理。</p>
 */
final class NewsHtmlTemplate {

    private NewsHtmlTemplate() {
    }

    /**
     * HTML 文本转义。对所有来自数据的文本（标题 / 摘要 / 源名 / note / 链接）调用，
     * 防止 XSS 与破版。转义顺序固定：先 {@code &}，避免二次转义。
     */
    static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#x27;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 内联样式表（单日页与归档页共用）。 */
    static final String STYLE = """
            <style>
              :root{--bg:#f4f5f7;--card:#fff;--ink:#1a1d24;--sub:#6b7280;--line:#e7e9ee;--brand:#c8102e;--brand-soft:#fbe9ec;--chip:#eef1f5;--shadow:0 1px 3px rgba(0,0,0,.06),0 8px 24px rgba(0,0,0,.04)}
              @media (prefers-color-scheme:dark){:root{--bg:#0f1115;--card:#181b22;--ink:#e8eaed;--sub:#9aa0aa;--line:#262a33;--brand:#ff5a6e;--brand-soft:#2a181c;--chip:#222630;--shadow:0 1px 3px rgba(0,0,0,.4)}}
              *{box-sizing:border-box;margin:0;padding:0}
              body{font-family:-apple-system,BlinkMacSystemFont,"PingFang SC","Microsoft YaHei",sans-serif;background:var(--bg);color:var(--ink);line-height:1.6;-webkit-font-smoothing:antialiased}
              .wrap{max-width:760px;margin:0 auto;padding:0 16px 64px}
              header{padding:28px 0 18px;text-align:center}
              .kicker{color:var(--brand);font-weight:700;letter-spacing:.18em;font-size:12px}
              h1{font-size:30px;margin:6px 0 2px;letter-spacing:-.5px}
              .date{color:var(--sub);font-size:14px}
              .nav{display:flex;align-items:center;justify-content:center;gap:10px;margin:14px 0 2px;flex-wrap:wrap}
              .nav a,.nav span.navbtn{display:inline-flex;align-items:center;border:1px solid var(--line);background:var(--card);color:var(--ink);padding:6px 13px;border-radius:999px;font-size:13px;font-weight:600;text-decoration:none;cursor:pointer}
              .nav a:hover{border-color:var(--brand);color:var(--brand)}
              .nav span.navbtn.disabled{opacity:.4;cursor:not-allowed}
              .nav select{border:1px solid var(--line);background:var(--card);color:var(--ink);padding:6px 10px;border-radius:999px;font-size:13px;font-weight:600;cursor:pointer}
              .nav .arch{color:var(--sub)}
              .brief{background:linear-gradient(135deg,var(--brand-soft),transparent);border:1px solid var(--line);border-radius:14px;padding:16px 18px;margin:18px 0 8px;font-size:14.5px}
              .brief b{color:var(--brand)}
              .search{position:relative;margin:14px 0 2px}
              .search input{width:100%;border:1px solid var(--line);background:var(--card);color:var(--ink);border-radius:12px;padding:11px 40px 11px 15px;font-size:14.5px;outline:none}
              .search input:focus{border-color:var(--brand)}
              .search .clr{position:absolute;right:12px;top:50%;transform:translateY(-50%);color:var(--sub);cursor:pointer;font-size:18px;line-height:1;display:none}
              .tabs{position:sticky;top:0;z-index:9;display:flex;gap:8px;overflow-x:auto;padding:12px 0;background:var(--bg);scrollbar-width:none}
              .tabs::-webkit-scrollbar{display:none}
              .tab{flex:0 0 auto;border:1px solid var(--line);background:var(--card);color:var(--sub);padding:7px 15px;border-radius:999px;font-size:13.5px;cursor:pointer;transition:.15s;font-weight:600;white-space:nowrap}
              .tab.active{background:var(--brand);color:#fff;border-color:var(--brand)}
              .card{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:16px 18px;margin-bottom:12px;box-shadow:var(--shadow);transition:.15s}
              .card:hover{transform:translateY(-1px);border-color:var(--brand)}
              .meta{display:flex;align-items:center;gap:8px;font-size:12px;color:var(--sub);margin-bottom:7px;flex-wrap:wrap}
              .src{background:var(--chip);padding:2px 9px;border-radius:6px;font-weight:600;color:var(--ink)}
              .cat{color:var(--brand);font-weight:700}
              .en{border:1px solid var(--line);padding:1px 6px;border-radius:5px;font-size:10.5px;color:var(--sub)}
              .stars{color:#f5a623;letter-spacing:1px}
              .title{font-size:17.5px;font-weight:650;letter-spacing:-.2px}
              .title a{color:inherit;text-decoration:none}
              .title a:hover{color:var(--brand);text-decoration:underline}
              .sum{margin-top:8px;font-size:14.5px;color:var(--ink)}
              .sum .ai{display:inline-block;font-size:11px;color:#fff;background:linear-gradient(135deg,#6d5efc,#a855f7);padding:1px 7px;border-radius:5px;margin-right:6px;font-weight:700;vertical-align:1px}
              .orig{display:inline-block;margin-top:10px;font-size:13px;color:var(--brand);text-decoration:none;font-weight:600}
              .orig:hover{text-decoration:underline}
              footer{text-align:center;color:var(--sub);font-size:12px;margin-top:28px;line-height:1.9}
              .hidden{display:none}
              .count{color:var(--sub);font-size:13px;margin:6px 2px 14px}
              .empty{color:var(--sub);font-size:14px;text-align:center;padding:28px 0;display:none}
              .arch-item{display:flex;align-items:center;justify-content:space-between;background:var(--card);border:1px solid var(--line);border-radius:14px;padding:14px 18px;margin-bottom:10px;box-shadow:var(--shadow);text-decoration:none;color:inherit;transition:.15s}
              .arch-item:hover{transform:translateY(-1px);border-color:var(--brand)}
              .arch-item .d{font-size:16px;font-weight:650}
              .arch-item .s{font-size:13px;color:var(--sub)}
            </style>""";

    /**
     * 单日页脚本：分类筛选 + 站内搜索（叠加 / 交集）+ 跨天往期导航。
     *
     * <p>{@code __DATES__} 占位由调用方替换为内联 JS 日期数组（倒序，字符串元素），
     * {@code __CURRENT__} 替换为当前页日期字符串。其余 {@code ${...}} 均为 JS 模板字符串。</p>
     */
    static final String SCRIPT = """
            <script>
              const DATES=__DATES__;
              const CURRENT=__CURRENT__;
              const tabs=document.getElementById('tabs');
              const cards=[...document.querySelectorAll('.card')];
              const countEl=document.getElementById('count');
              const emptyEl=document.getElementById('empty');
              const searchEl=document.getElementById('search');
              const clrEl=document.getElementById('search-clear');
              let curCat='all';
              function apply(){
                const kw=(searchEl?searchEl.value:'').trim().toLowerCase();
                let n=0;
                cards.forEach(c=>{
                  const catOk=curCat==='all'||c.dataset.cat===curCat;
                  const hay=(c.dataset.search||'');
                  const kwOk=kw===''||hay.indexOf(kw)>=0;
                  const show=catOk&&kwOk;
                  c.classList.toggle('hidden',!show);
                  if(show)n++;
                });
                countEl.textContent=`显示 ${n} 条`;
                if(emptyEl)emptyEl.style.display=n===0?'block':'none';
                if(clrEl)clrEl.style.display=kw===''?'none':'block';
              }
              if(tabs)tabs.addEventListener('click',e=>{
                const t=e.target.closest('.tab');if(!t)return;
                tabs.querySelectorAll('.tab').forEach(x=>x.classList.remove('active'));
                t.classList.add('active');curCat=t.dataset.cat;apply();
              });
              if(searchEl)searchEl.addEventListener('input',apply);
              if(clrEl)clrEl.addEventListener('click',()=>{searchEl.value='';searchEl.focus();apply();});
              const picker=document.getElementById('date-picker');
              if(picker)picker.addEventListener('change',()=>{
                const d=picker.value;if(d&&d!==CURRENT)location.href='./'+d+'.html';
              });
              apply();
            </script>""";
}
