package netrunner
//
//
//
//
import hrf.colmat._
import hrf.logger._
//
//
//
//

import hrf.elem._

package object elem {
    object styles extends BaseStyleMapping("netrunner") {
        import rules._

        Corp --> color("#6fb7f0")
        Runner --> color("#ff9a5c")

        Anarch --> color("#ff6a3d")
        Criminal --> color("#6c8cff")
        Shaper --> color("#5ad65a")
        HaasBioroid --> color("#b48cff")
        Jinteki --> color("#ff5c7a")
        NBN --> color("#ffd84d")
        Weyland --> color("#4fbf8a")
        Neutral --> color("#b8b8b8")

        // card images (ex-based so they scale with the pane font size)
        object card extends CustomStyle(display("block"), width("10.5ex"), height("14.67ex"), border.radius("0.5ex"))
        object cardSmall extends CustomStyle(display("block"), width("7.5ex"), height("10.48ex"), border.radius("0.4ex"))
        object cardTiny extends CustomStyle(display("block"), width("5ex"), height("6.98ex"), border.radius("0.3ex"))
        object cardIceImg extends CustomStyle(display("block"), position("absolute"), width("7.5ex"), height("10.48ex"), left("1.5ex"), top("-1.5ex"), transform("rotate(90deg)"), border.radius("0.4ex"))
        object iceBox extends CustomStyle(display("block"), position("relative"), width("10.5ex"), height("7.5ex"), margin.ver("0.25ex"), margin.hor("auto"))
        object artwork extends CustomStyle(max.height("88vh"), max.width("60vw"), border.radius("1.5ex"))
        object zoomImg extends CustomStyle(display("block"), height("64vh"), max.height("630px"), border.radius("1.2ex"))
        // hand cards in the action pane
        object cardHand extends CustomStyle(display("block"), width("9ex"), height("12.57ex"), border.radius("0.5ex"))
        object cardGallery extends CustomStyle(display("block"), width("15ex"), height("20.95ex"), border.radius("0.7ex"), margin.ver("0"), margin.hor("auto"))
        // a preset deck in the action pane: identity thumbnail, deck name, identity
        object presetOption extends CustomStyle(display("flex"), align.items("center"), justify.content("flex-start"), text.align("left"))
        object presetThumb extends CustomStyle(margin.right("1ex"), flex.shrink("0"))
        object presetIdentity extends CustomStyle(font.size("75%"), color("#a0a0a0"))
        object handSlot extends CustomStyle(display("inline-block"), position("relative"), vertical.align("top"))
        object handTitle extends CustomStyle(color("#a0a0a0"))
        // the part of a board pane that is scaled to fit (see UI.fitPane); the header keeps the pane font size
        object boardArea extends CustomStyle(display("block"))

        object slot extends CustomStyle(display("inline-block"), margin("0.3ex"), vertical.align("top"), text.align("center"), position("relative"), font.size("80%"))
        object slotHand extends CustomStyle(display("inline-block"), margin("0.2ex"), vertical.align("top"), text.align("center"), position("relative"), font.size("80%"))
        object hostedRow extends CustomStyle(display("flex"), flex.wrap("wrap"), justify.content("center"), margin.top("-1.2ex"))

        object unrezzed extends CustomStyle(filter("brightness(0.55) saturate(0.6)"))
        object facedown extends CustomStyle(filter("brightness(0.7)"))
        object highlighted extends CustomStyle(outline.color("#ffffff"), outline.style("solid"), outline.width("0.3ex"), border.radius("0.5ex"))
        object encountered extends CustomStyle(outline.color("#ff9a5c"), outline.style("solid"), outline.width("0.4ex"), border.radius("0.5ex"))
        object actionable extends CustomStyle(cursor("pointer"))

        // badges on cards
        object badges extends CustomStyle(position("absolute"), left("0"), right("0"), bottom("0.4ex"), text.align("center"), pointer.events("none"))
        object badge extends CustomStyle(font.size("85%"), color("#f0f0f0"), background.color("#000000d0"), border.radius("0.6ex"), padding.ver("0"), padding.hor("0.5ex"), display("inline-block"), margin("0.1ex"), white.space("nowrap"), font.weight("bold"))
        object badgeAdv extends CustomStyle(color("#ffd84d"))
        object badgeVirus extends CustomStyle(color("#5ad65a"))
        object badgeCredit extends CustomStyle(color("#6fb7f0"))
        object badgePower extends CustomStyle(color("#ff6a3d"))
        object badgeStr extends CustomStyle(color("#ffffff"), background.color("#5a2a8acc"))
        object badgeMark extends CustomStyle(color("#ffffff"), background.color("#b03a00e0"))
        object markLabel extends CustomStyle(color("#ff9a5c"), font.weight("bold"))
        // placeholder for a card without an image: the card back with the title on top
        object placeholder extends CustomStyle(position("relative"), display("inline-block"))
        object placeholderTitle extends CustomStyle(position("absolute"), left("4%"), right("4%"), top("30%"), color("#ffffff"), background.color("#000000c0"), border.radius("0.5ex"), padding("0.3ex"), font.size("85%"), font.weight("bold"), white.space("normal"), pointer.events("none"), line.height("1.2"))
        object placeholderSmall extends CustomStyle(font.size("65%"), top("22%"))
        object tagTiny extends CustomStyle(font.size("55%"), top("0.1ex"), left("0.1ex"))
        object tag extends CustomStyle(position("absolute"), top("0.3ex"), left("0.3ex"), font.size("75%"), color("#dddddd"), background.color("#000000c0"), padding.ver("0"), padding.hor("0.4ex"), border.radius("0.4ex"), pointer.events("none"))

        // servers
        object server extends CustomStyle(display("inline-block"), margin("0.3ex"), vertical.align("top"), text.align("center"), border.style("solid"), border.width("1px"), border.color("#3a3a3a"), border.radius("0.8ex"), padding.ver("0.3ex"), padding.hor("0.4ex"), min.width("11.5ex"), background.color("#1c1c1c"))
        object serverRun extends CustomStyle(border.color("#ff9a5c"), border.width("2px"), background.color("#2a2018"))
        object serverName extends CustomStyle(font.size("clamp(12px, 75%, 20px)"), color("#b0b0b0"), display("inline-block"), white.space("nowrap"), padding.ver("0"), padding.hor("0.6ex"), margin.bottom("0.2ex"))
        object serverCount extends CustomStyle(font.size("clamp(10px, 70%, 16px)"), color("#8a8a8a"), display("block"))
        object iceColumn extends CustomStyle(display("flex"), flex.direction("column"), align.items("center"), min.height("1ex"))
        object rootRow extends CustomStyle(display("flex"), flex.wrap("wrap"), justify.content("center"), align.items("flex-start"), min.height("10ex"))
        object marker extends CustomStyle(display("block"), font.size("clamp(11px, 75%, 18px)"), color("#ff9a5c"), font.weight("bold"), margin.ver("0.1ex"), margin.hor("0"))
        object serversRow extends CustomStyle(display("flex"), flex.wrap("wrap"), justify.content("center"), align.items("flex-start"))

        object row extends CustomStyle(display("flex"), flex.wrap("wrap"), justify.content("center"), align.items("flex-start"))
        object rowStart extends CustomStyle(display("flex"), flex.wrap("wrap"), justify.content("flex-start"), align.items("flex-start"))
        object section extends CustomStyle(display("inline-block"), vertical.align("top"), margin.ver("0.3ex"), margin.hor("0.6ex"), text.align("center"))
        object sectionTitle extends CustomStyle(font.size("clamp(10px, 70%, 16px)"), color("#8a8a8a"), display("block"))

        // header
        object header extends CustomStyle(display("flex"), flex.wrap("wrap"), justify.content("center"), align.items("center"), margin.bottom("0.3ex"), padding.ver("0.2ex"), padding.hor("0.5ex"), border.radius("1ex"))
        object name extends CustomStyle(font.weight("bold"), font.size("115%"), margin.right("0.8ex"))
        object headerId extends CustomStyle(font.size("88%"))
        object headerItem extends CustomStyle(margin.top("0"), margin.right("0.8ex"), margin.bottom("0"), margin.left("0"))
        // status banner: every stat as a large badge (UI.statusBanner, UI.bigStat): name and identity, then the badges
        object bannerWho extends CustomStyle(display("inline-flex"), flex.direction("column"), align.items("flex-start"), text.align("left"), line.height("1.15"), margin.right("0.8ex"), max.width("36ex"), flex.shrink("0"))
        object bannerName extends CustomStyle(white.space("nowrap"))
        object bannerId extends CustomStyle(font.size("82%"), white.space("nowrap"), overflow("hidden"), text.overflow("ellipsis"), max.width("36ex"))
        object bannerStats extends CustomStyle(display("inline-flex"), flex.wrap("wrap"), align.items("stretch"), justify.content("center"))
        // the badges of one group (score / clicks and credits / the side's stats / turn); the groups are set apart by space
        object statGroup extends CustomStyle(display("inline-flex"), flex.wrap("wrap"), align.items("stretch"), justify.content("center"))
        object bannerGroups extends CustomStyle(SimpleCSSRule("column-gap", "3ex"))
        object bigStat extends CustomStyle(display("inline-flex"), flex.direction("column"), align.items("center"), justify.content("center"), min.width("4.4ex"), padding.ver("0.2ex"), padding.hor("0.8ex"), margin.ver("0.15ex"), margin.hor("0.4ex"), border.radius("0.8ex"), border.style("solid"), border.width("0.25ex"), border.color("transparent"), line.height("1"), white.space("nowrap"))
        // the icon and the value side by side
        object bigValue extends CustomStyle(display("inline-flex"), align.items("center"), SimpleCSSRule("gap", "0.22ex"), font.size("220%"), font.weight("bold"))
        object bigWord extends CustomStyle(font.size("130%"), padding.ver("0.35ex"))
        object bigLabel extends CustomStyle(font.size("62%"), color("#d0d0d0"), margin.top("0.1ex"))
        object bigCredit extends CustomStyle(background.color("#163250"), color("#8fd0ff"))
        object bigClick extends CustomStyle(background.color("#3b3810"), color("#ffe066"))
        object bigPoints extends CustomStyle(background.color("#32214f"), color("#d6b8ff"))
        object bigTag extends CustomStyle(background.color("#5a1f18"), color("#ff8a7a"))
        object bigBadPub extends CustomStyle(background.color("#4a1836"), color("#ff8ad0"))
        object bigThreat extends CustomStyle(background.color("#4a2e10"), color("#ffb060"))
        object bigHand extends CustomStyle(background.color("#333333"), color("#ececec"))
        object bigLink extends CustomStyle(background.color("#123a32"), color("#6fe0c0"))
        object bigMU extends CustomStyle(background.color("#1f3a1a"), color("#9be27a"))
        object bigCore extends CustomStyle(background.color("#3a1446"), color("#e08aff"))
        object bigMark extends CustomStyle(background.color("#4a2a10"), color("#ffb27a"))
        object bigTurn extends CustomStyle(background.color("#3a3a3a"), color("#ffffff"))
        // stat icons, drawn in the badge's colour (an SVG as a CSS mask over currentColor): credit, click, agenda
        // points, link and MU are the Netrunner symbols of jinteki.net's icon font (resources/public/fonts/netrunner.svg,
        // MIT); the others are drawn here to match
        def svgMask(svg : String) = RepeatCSSRule($("mask-image", "-webkit-mask-image"), "url(\"data:image/svg+xml," + svg.replace("%", "%25").replace("\"", "'").replace("<", "%3C").replace(">", "%3E").replace("#", "%23") + "\")")
        object statIcon extends CustomStyle(display("inline-block"), width("0.85em"), height("0.85em"), background.color("currentColor"), SimpleCSSRule("flex-shrink", "0"), RepeatCSSRule($("mask-size", "-webkit-mask-size"), "contain"), RepeatCSSRule($("mask-repeat", "-webkit-mask-repeat"), "no-repeat"), RepeatCSSRule($("mask-position", "-webkit-mask-position"), "center"))
        object iconCredit extends CustomStyle(svgMask("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 533 1024'><path transform='translate(0 870.4) scale(1 -1)' d='M268.592 793.25l259.702-233.965v-435.173l-259.702-233.963-259.701 233.963v435.173l259.701 233.965zM467.461 152.189v379.019l-198.87 177.814-191.85-175.473 191.85-182.493v-198.869l-198.87 189.51v-189.51l198.87-175.472 198.87 175.472z'/></svg>"))
        object iconClick extends CustomStyle(svgMask("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1045 1024'><path transform='translate(0 870.4) scale(1 -1)' d='M651.134 795.851c258.535-2.423 414.633-213.14 343.902-472.3-70.734-259.16-341.464-472.299-602.438-472.299-85.365 0-158.535 21.798-217.073 60.55l-36.583-38.753 31.706 43.596c-119.512 79.927-173.171 232.517-126.829 406.903 70.732 259.159 339.023 469.879 597.559 472.301l14.635 36.329-4.879-36.328zM621.866 631.151l-31.706-188.92c46.34-16.956 70.732-62.973 56.097-118.681-19.512-67.818-90.242-125.946-160.975-125.946-9.757 0-21.953 2.423-31.706 4.843-2.44 2.423-4.878 2.423-7.316 2.423l-143.902-155.010c36.585-21.799 82.927-33.908 134.148-33.908 170.733 0 346.341 138.058 392.683 307.599 43.902 162.277-46.34 297.913-207.317 307.601l-0.004-0.001zM512.11 442.231l68.294 188.92c-163.415-9.689-326.829-145.323-370.731-307.601-29.268-108.991 0-203.451 73.171-259.158l124.392 169.544-4.878 9.689c-12.195 21.798-17.074 48.441-9.757 79.926 14.635 55.707 63.415 101.727 119.512 118.681l-0.002-0.001zM543.817 418.011c-51.219 0-104.878-41.176-119.512-94.461-14.635-50.862 17.074-94.46 68.293-94.46 53.657 0 107.316 43.596 121.952 94.46 14.634 53.286-17.075 94.461-70.733 94.461v0zM102.353 323.549c-41.465-150.166 4.878-280.958 107.316-353.62l46.341 58.128c-82.927 62.974-119.512 171.966-85.365 295.491 48.781 186.499 239.025 339.087 424.389 348.775l26.829 65.397c-226.829-4.843-458.536-188.919-519.511-414.172v0.002zM407.231-90.619c229.268 0 465.853 186.497 529.268 414.171 58.536 225.251-73.171 409.327-295.122 414.172l-12.195-65.396c182.927-9.688 290.244-162.277 239.024-348.776-51.218-191.341-251.22-346.352-441.463-346.352-60.974 0-112.194 14.532-153.656 41.176l-51.218-55.707c51.218-33.908 114.634-53.285 185.366-53.285l-0.004-0.002zM504.792 267.843c-31.706 0-48.781 26.645-41.465 55.707 7.316 31.486 39.025 55.707 70.733 55.707 31.705 0 48.78-24.221 41.464-55.707-9.757-29.063-41.464-55.707-70.732-55.707v0z'/></svg>"))
        object iconPoints extends CustomStyle(svgMask("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1024 1024'><path transform='translate(0 870.4) scale(1 -1)' d='M472.264 476.966l146.18 52.065v238.295l-146.18-52.064v-238.296zM195.921 208.634v506.629l-90.112 50.061v-588.73l90.112 32.040zM215.945 711.256h236.293v-500.621h-236.293v500.621zM460.249 731.281l144.178 52.064-82.102 48.060h-260.323l-142.176-52.064 88.109-48.059h252.314zM582.401 392.862h236.291v-488.606h-236.291v488.606zM628.457 513.011l-136.168-50.062h-8.010l90.112-50.061h252.312l146.182 52.065-82.102 48.059h-262.326zM838.718 394.864v-486.603l148.184 52.063v488.607l-148.184-54.067zM562.376 396.867l-90.112 48.059v-238.295l90.112-52.065v242.301zM462.251 462.947v0 0zM133.844 74.466l-90.112 50.061v-218.27l90.112-50.062v218.271zM153.868 70.462h236.293v-220.274h-236.293v220.274zM556.368-93.742v220.273l-146.181-52.065v-220.274l146.181 52.066zM145.858 90.485h252.313l144.179 52.065-80.1 48.058h-262.325l-142.175-52.063 88.108-48.060z'/></svg>"))
        object iconLink extends CustomStyle(svgMask("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 853 1024'><path transform='translate(0 870.4) scale(1 -1)' d='M567.555 147.382h-172.701v59.082h113.619v436.297h-436.297v-436.297h156.794v-59.082h-215.876v554.459h554.459zM840.24-100.304h-554.459v554.459h170.428v-56.809h-111.346v-438.569h436.297v438.569h-156.794v56.809h215.876z'/></svg>"))
        object iconMU extends CustomStyle(svgMask("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1408 1024'><path transform='translate(0 870.4) scale(1 -1)' d='M1295.508 749.625h73.849v-829.37h-357.882v22.722h-45.445v-93.729h-729.961l-19.883 22.723-187.462 187.461v772.568h937.305v-105.090h45.445v22.723l284.034-0.008zM1295.508 559.323v116.454h-210.186v-22.723h-193.14v102.252h-789.607v-664.636l164.737-164.737h624.87v90.891h193.14v-22.722h210.185v116.452h-210.185v-22.723h-193.14v116.453h193.14v-22.723h210.185v116.454h-210.185v-22.723h-193.14v116.453h193.14v-19.884h210.185v116.453h-210.185v-25.563h-193.14v116.453h193.14v-19.884l210.186 0.004z'/></svg>"))
        object iconTurn extends CustomStyle(svgMask("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'><path fill='none' stroke='#000' stroke-width='2.4' stroke-linecap='round' d='M19.5 12a7.5 7.5 0 1 1-2.2-5.3'/><path d='M21 3v7h-7z'/></svg>"))
        object iconBadPub extends CustomStyle(svgMask("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'><path d='M2 3h4v11H2zM8 3h8.5c.9 0 1.7.6 1.9 1.5l1.8 7c.3 1.2-.6 2.5-1.9 2.5H14l.9 4.1c.2 1-.3 2-1.2 2.4l-.7.3L8 15z'/></svg>"))
        object iconThreat extends CustomStyle(svgMask("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'><path fill-rule='evenodd' d='M12 2 1 21h22zM11 9h2v6h-2zm0 8h2v2h-2z'/></svg>"))
        object iconHand extends CustomStyle(svgMask("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'><rect x='3' y='5' width='11' height='15' rx='1.5' transform='rotate(-12 8.5 12.5)' fill='none' stroke='#000' stroke-width='2'/><rect x='10' y='4' width='11' height='15' rx='1.5' transform='rotate(10 15.5 11.5)'/></svg>"))
        object iconTag extends CustomStyle(svgMask("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'><path fill-rule='evenodd' d='M2.5 3.5a1 1 0 0 1 1-1h7.6a1 1 0 0 1 .7.3l9.6 9.6a1 1 0 0 1 0 1.4l-7.6 7.6a1 1 0 0 1-1.4 0L2.8 11.8a1 1 0 0 1-.3-.7zM7.5 9.5a2 2 0 1 0 0-4 2 2 0 0 0 0 4z'/></svg>"))
        object iconCore extends CustomStyle(svgMask("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'><path d='M11 4.5C11 3.1 9.9 2 8.5 2 7.3 2 6.3 2.9 6 4c-1.7.2-3 1.6-3 3.3 0 .6.2 1.2.5 1.7C2.6 9.6 2 10.6 2 11.8c0 1.3.8 2.4 1.9 2.8-.1.3-.1.6-.1.9 0 1.9 1.5 3.4 3.4 3.4.4 1.8 1.9 3.1 3.8 3.1z'/><path d='M13 4.5C13 3.1 14.1 2 15.5 2c1.2 0 2.2.9 2.5 2 1.7.2 3 1.6 3 3.3 0 .6-.2 1.2-.5 1.7.9.6 1.5 1.6 1.5 2.8 0 1.3-.8 2.4-1.9 2.8.1.3.1.6.1.9 0 1.9-1.5 3.4-3.4 3.4-.4 1.8-1.9 3.1-3.8 3.1z'/></svg>"))
        object iconMark extends CustomStyle(svgMask("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'><g fill='none' stroke='#000' stroke-width='2'><circle cx='12' cy='12' r='7.5'/><path d='M12 1v6M12 17v6M1 12h6M17 12h6'/></g><circle cx='12' cy='12' r='2'/></svg>"))
        object bigEmphasis extends CustomStyle(border.color("#ffffffd0"), SimpleCSSRule("box-shadow", "0 0 1ex #ffffff55"))
        object bigAlert extends CustomStyle(border.color("#ff5a4ad0"), SimpleCSSRule("box-shadow", "0 0 0.8ex #ff5a4a66"))
        // a zero (no tags, no bad publicity...): kept in place, subdued
        object bigZero extends CustomStyle(opacity("0.55"), filter("saturate(0.45)"))
        object bigSpent extends CustomStyle(opacity("0.5"))
        object headerActiveCorp extends CustomStyle(background.color("#6fb7f01f"), SimpleCSSRule("box-shadow", "inset 0 -0.3ex 0 #6fb7f0"))
        object headerActiveRunner extends CustomStyle(background.color("#ff9a5c1f"), SimpleCSSRule("box-shadow", "inset 0 -0.3ex 0 #ff9a5c"))

        object dim extends CustomStyle(color("#808080"))
        object runLine extends CustomStyle(color("#ff9a5c"), font.weight("bold"), margin.ver("0.2ex"), margin.hor("0"))
        object promptLine extends CustomStyle(color("#d3d3d3"), margin.ver("0.2ex"), margin.hor("0"), font.size("95%"))

        object status extends CustomStyle(
            text.align("center"),
            font.size("90%"),
            overflow.x("hidden"),
            overflow.y("auto"),
            padding("0.5ex")
        )
        object hand extends CustomStyle(display("flex"), flex.wrap("wrap"), justify.content("center"), align.items("flex-start"), margin.top("0.3ex"))
        object cardText extends CustomStyle(white.space("pre-wrap"), text.align("left"), max.width("46ex"), font.size("90%"), padding("1ex"), line.height("1.35"))
        object zoomText extends CustomStyle(white.space("pre-wrap"), text.align("left"), width("34ex"), font.size("85%"), padding.ver("0.5ex"), padding.hor("1ex"), line.height("1.3"), color("#e0e0e0"))
        object textarea extends CustomStyle(width("60ex"), height("40ex"), font.size("90%"), color("#d3d3d3"), background.color("#111111"))
        object selected extends CustomStyle(outline.color("#ffffff"), outline.style("solid"), outline.width("0.3vmin"))
        object subBroken extends CustomStyle(color("#808080"), text.decoration.line("line-through"))
        object viewcard extends CustomStyle(display("inline-block"), margin("0.3ex"))
        object clickable extends CustomStyle(cursor("pointer"))

        // menus and galleries
        object menuTitle extends CustomStyle(font.size("120%"), font.weight("bold"), margin("1ex"))
        object menuItem extends CustomStyle(display("block"), margin.ver("0.6ex"), margin.hor("1ex"), padding.ver("0.6ex"), padding.hor("2ex"), outline.color("#999999"), outline.style("solid"), outline.width("0.3vmin"), background.color("#111111"), cursor("pointer"), text.align("center"), min.width("30ex"))
        object menuItemOff extends CustomStyle(display("block"), margin.ver("0.6ex"), margin.hor("1ex"), padding.ver("0.6ex"), padding.hor("2ex"), outline.color("#444444"), outline.style("solid"), outline.width("0.3vmin"), background.color("#111111"), color("#777777"), text.align("center"), min.width("30ex"))
        object menuQ extends CustomStyle(font.size("85%"), color("#a0a0a0"), display("block"))
        object gallery extends CustomStyle(display("flex"), flex.wrap("wrap"), justify.content("center"), align.items("flex-start"))
        object galleryItem extends CustomStyle(display("inline-block"), margin("0.6ex"), padding("0.6ex"), text.align("center"), cursor("pointer"), outline.color("#555555"), outline.style("solid"), outline.width("1px"), border.radius("0.8ex"), background.color("#181818"), width("16ex"), vertical.align("top"))
        object galleryName extends CustomStyle(display("block"), font.weight("bold"), margin.top("0.4ex"), white.space("normal"))
        // gallery filter tabs (faction / era) with counts
        object tabRow extends CustomStyle(display("flex"), flex.wrap("wrap"), justify.content("center"), margin.ver("0.3ex"), margin.hor("0"))
        object tab extends CustomStyle(display("inline-block"), margin.ver("0.2ex"), margin.hor("0.25ex"), padding.ver("0.15ex"), padding.hor("0.9ex"), border.radius("1ex"), border.style("solid"), border.width("1px"), border.color("#444444"), background.color("#1a1a1a"), cursor("pointer"), font.size("clamp(12px, 100%, 19px)"), white.space("nowrap"))
        object tabOn extends CustomStyle(border.color("#dddddd"), background.color("#3a3a3a"), font.weight("bold"))
        object tabCount extends CustomStyle(color("#8a8a8a"), font.size("85%"))
        // which side is choosing a deck (UI.deckSelection, UI.deckSteps, UI.sideHint)
        object deckTitle extends CustomStyle(display("block"), margin.top("0.3ex"))
        object sideTitle extends CustomStyle(font.size("160%"), font.weight("bold"))
        object sideTitleRest extends CustomStyle(font.size("160%"), font.weight("bold"), margin.right("0.6ex"))
        object sideName extends CustomStyle(font.weight("bold"))
        object sideTag extends CustomStyle(font.weight("bold"), font.size("110%"), border.style("solid"), border.width("0.25ex"), border.radius("0.8ex"), padding.ver("0"), padding.hor("0.6ex"), margin.right("1ex"))
        object deckSteps extends CustomStyle(display("block"), margin.ver("0.3ex"))
        object deckStep extends CustomStyle(display("inline-block"), padding.ver("0.15ex"), padding.hor("0.8ex"), margin.hor("0.3ex"), border.radius("1ex"), border.style("solid"), border.width("1px"), border.color("#444444"), background.color("#1a1a1a"))
        object deckStepOn extends CustomStyle(border.width("0.25ex"), background.color("#262626"))
        object sideHint extends CustomStyle(display("block"), margin.ver("0.3ex"), font.size("105%"))
        object galleryInfo extends CustomStyle(display("block"), font.size("80%"), color("#9a9a9a"), white.space("normal"))
        // deck preview before the deck is confirmed (UI.deckPreviewElem)
        object previewHead extends CustomStyle(display("block"), margin.bottom("0.4ex"))
        object previewTitle extends CustomStyle(font.size("135%"), font.weight("bold"), margin.right("1ex"))
        object previewSummary extends CustomStyle(color("#c8c8c8"))
        object previewOk extends CustomStyle(color("#5ad65a"))
        object previewBad extends CustomStyle(color("#ffa500"))
        object previewHint extends CustomStyle(font.size("85%"), color("#9a9a9a"))
        object previewLink extends CustomStyle(color("#8fd0ff"), cursor("pointer"), text.decoration.line("underline"))
        object previewSection extends CustomStyle(display("inline-block"), vertical.align("top"), margin.ver("0.2ex"), margin.hor("0.5ex"), text.align("center"), border.style("solid"), border.width("1px"), border.color("#333333"), border.radius("0.8ex"), padding.ver("0.2ex"), padding.hor("0.3ex"), background.color("#181818"))
        object previewCount extends CustomStyle(display("block"), line.height("1.25"), white.space("nowrap"), color("#c8c8c8"))
        object countBadge extends CustomStyle(font.weight("bold"), color("#ffffff"), font.size("115%"))
        object influenceBadge extends CustomStyle(letter.spacing("-0.05em"), margin.left("0.5ex"), font.size("90%"))
        // the Confirm button of a previewed deck in the action pane
        object confirmButton extends CustomStyle(display("flex"), align.items("center"), justify.content("center"), margin.ver("0.6ex"), margin.hor("2ex"), padding.ver("0.6ex"), padding.hor("1ex"), outline.color("#5ad65a"), outline.style("solid"), outline.width("0.35vmin"), background.color("#123018"), cursor("pointer"), font.size("120%"), font.weight("bold"), white.space("normal"), color("#ffffff"))
        object confirmBack extends CustomStyle(display("flex"), align.items("center"), justify.content("center"), margin.ver("0.4ex"), margin.hor("4ex"), padding.ver("0.4ex"), padding.hor("1ex"), outline.color("#999999"), outline.style("solid"), outline.width("0.3vmin"), background.color("#111111"), cursor("pointer"))
        object pileTitle extends CustomStyle(font.size("110%"), font.weight("bold"), margin("1ex"), display("block"))
    }

    object borders extends BaseStyleMapping("netrunner-border") {
        import rules._

        Corp --> border.color("#6fb7f0")
        Runner --> border.color("#ff9a5c")
    }

    implicit class ElemInt(val n : Int) extends AnyVal {
        def cards = (n != 1).?(n.hl ~ " cards").|("1 card".hl)
        def credits = (n != 1).?(n.hl ~ " credits").|("1 credit".hl)
        def clicks = (n != 1).?(n.hl ~ " clicks").|("1 click".hl)
        def tags = (n != 1).?(n.hl ~ " tags").|("1 tag".hl)
    }
}
