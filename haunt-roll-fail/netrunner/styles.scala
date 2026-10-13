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
        object card extends CustomStyle(display("block"), width("10.5ex"), border.radius("0.5ex"))
        object cardSmall extends CustomStyle(display("block"), width("7.5ex"), border.radius("0.4ex"))
        object cardTiny extends CustomStyle(display("block"), width("5ex"), border.radius("0.3ex"))
        object cardIceImg extends CustomStyle(display("block"), position("absolute"), width("7.5ex"), left("1.5ex"), top("-1.5ex"), transform("rotate(90deg)"), border.radius("0.4ex"))
        object iceBox extends CustomStyle(display("block"), position("relative"), width("10.5ex"), height("7.5ex"), margin("0.25ex auto"))
        object artwork extends CustomStyle(max.height("88vh"), max.width("60vw"), border.radius("1.5ex"))
        object zoomImg extends CustomStyle(display("block"), height("60vh"), border.radius("1.2ex"))

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
        object badge extends CustomStyle(font.size("85%"), color("#f0f0f0"), background.color("#000000d0"), border.radius("0.6ex"), padding("0 0.5ex"), display("inline-block"), margin("0.1ex"), white.space("nowrap"), font.weight("bold"))
        object badgeAdv extends CustomStyle(color("#ffd84d"))
        object badgeVirus extends CustomStyle(color("#5ad65a"))
        object badgeCredit extends CustomStyle(color("#6fb7f0"))
        object badgePower extends CustomStyle(color("#ff6a3d"))
        object badgeStr extends CustomStyle(color("#ffffff"), background.color("#5a2a8acc"))
        object badgeMark extends CustomStyle(color("#ffffff"), background.color("#b03a00e0"))
        object tag extends CustomStyle(position("absolute"), top("0.3ex"), left("0.3ex"), font.size("75%"), color("#dddddd"), background.color("#000000c0"), padding("0 0.4ex"), border.radius("0.4ex"), pointer.events("none"))

        // servers
        object server extends CustomStyle(display("inline-block"), margin("0.3ex"), vertical.align("bottom"), text.align("center"), border.style("solid"), border.width("1px"), border.color("#3a3a3a"), border.radius("0.8ex"), padding("0.3ex 0.4ex"), min.width("11.5ex"), background.color("#1c1c1c"))
        object serverRun extends CustomStyle(border.color("#ff9a5c"), border.width("2px"), background.color("#2a2018"))
        object serverName extends CustomStyle(font.size("85%"), color("#b0b0b0"), display("block"), white.space("nowrap"))
        object serverCount extends CustomStyle(font.size("80%"), color("#8a8a8a"), display("block"))
        object iceColumn extends CustomStyle(display("block"), min.height("1ex"))
        object rootRow extends CustomStyle(display("flex"), flex.wrap("wrap"), justify.content("center"), align.items("flex-end"), min.height("10ex"))
        object marker extends CustomStyle(display("block"), font.size("80%"), color("#ff9a5c"), font.weight("bold"), margin("0.1ex 0"))
        object serversRow extends CustomStyle(display("flex"), flex.wrap("wrap"), justify.content("center"), align.items("flex-end"))

        object row extends CustomStyle(display("flex"), flex.wrap("wrap"), justify.content("center"), align.items("flex-start"))
        object rowStart extends CustomStyle(display("flex"), flex.wrap("wrap"), justify.content("flex-start"), align.items("flex-start"))
        object section extends CustomStyle(display("inline-block"), vertical.align("top"), margin("0.3ex 0.6ex"), text.align("center"))
        object sectionTitle extends CustomStyle(font.size("80%"), color("#8a8a8a"), display("block"))

        // header
        object header extends CustomStyle(display("flex"), flex.wrap("wrap"), justify.content("center"), align.items("center"), margin.bottom("0.3ex"))
        object name extends CustomStyle(font.weight("bold"), font.size("115%"), margin.right("0.8ex"))
        object headerItem extends CustomStyle(margin("0 0.8ex 0 0"))
        object stat extends CustomStyle(display("inline-block"), margin("0.15ex 0.3ex"), padding("0.1ex 0.7ex"), border.radius("0.8ex"), background.color("#2e2e2e"), white.space("nowrap"), font.size("95%"))
        object statValue extends CustomStyle(font.weight("bold"), color("#ffffff"), font.size("110%"))
        object statCredit extends CustomStyle(background.color("#1f3a55"))
        object statClick extends CustomStyle(background.color("#3a3a1f"))
        object statTag extends CustomStyle(background.color("#55261f"))
        object statBadPub extends CustomStyle(background.color("#55261f"))
        object statPoints extends CustomStyle(background.color("#3a2a55"))
        object statLink extends CustomStyle(background.color("#1f4a3a"))
        object statMU extends CustomStyle(background.color("#2a3a2a"))
        object statHand extends CustomStyle(background.color("#333333"))
        object active extends CustomStyle(color("#ffffff"), background.color("#444444"), border.radius("0.8ex"), padding("0.1ex 0.8ex"), font.size("85%"), margin.left("1ex"))
        object dim extends CustomStyle(color("#808080"))
        object runLine extends CustomStyle(color("#ff9a5c"), font.weight("bold"), margin("0.2ex 0"))
        object promptLine extends CustomStyle(color("#d3d3d3"), margin("0.2ex 0"), font.size("95%"))

        object status extends CustomStyle(
            text.align("center"),
            font.size("90%"),
            overflow.x("hidden"),
            overflow.y("auto"),
            padding("0.5ex")
        )
        object hand extends CustomStyle(display("flex"), flex.wrap("wrap"), justify.content("center"), align.items("flex-start"), margin.top("0.3ex"))
        object cardText extends CustomStyle(white.space("pre-wrap"), text.align("left"), max.width("46ex"), font.size("90%"), padding("1ex"), line.height("1.35"))
        object zoomText extends CustomStyle(white.space("pre-wrap"), text.align("left"), width("34ex"), font.size("85%"), padding("0.5ex 1ex"), line.height("1.3"), color("#e0e0e0"))
        object textarea extends CustomStyle(width("60ex"), height("40ex"), font.size("90%"), color("#d3d3d3"), background.color("#111111"))
        object selected extends CustomStyle(outline.color("#ffffff"), outline.style("solid"), outline.width("0.3vmin"))
        object subBroken extends CustomStyle(color("#808080"), text.decoration.line("line-through"))
        object viewcard extends CustomStyle(display("inline-block"), margin("0.3ex"))
        object clickable extends CustomStyle(cursor("pointer"))

        // menus and galleries
        object menuTitle extends CustomStyle(font.size("120%"), font.weight("bold"), margin("1ex"))
        object menuItem extends CustomStyle(display("block"), margin("0.6ex 1ex"), padding("0.6ex 2ex"), outline.color("#999999"), outline.style("solid"), outline.width("0.3vmin"), background.color("#111111"), cursor("pointer"), text.align("center"), min.width("30ex"))
        object menuItemOff extends CustomStyle(display("block"), margin("0.6ex 1ex"), padding("0.6ex 2ex"), outline.color("#444444"), outline.style("solid"), outline.width("0.3vmin"), background.color("#111111"), color("#777777"), text.align("center"), min.width("30ex"))
        object menuQ extends CustomStyle(font.size("85%"), color("#a0a0a0"), display("block"))
        object gallery extends CustomStyle(display("flex"), flex.wrap("wrap"), justify.content("center"), align.items("flex-start"))
        object galleryItem extends CustomStyle(display("inline-block"), margin("0.6ex"), padding("0.6ex"), text.align("center"), cursor("pointer"), outline.color("#555555"), outline.style("solid"), outline.width("1px"), border.radius("0.8ex"), background.color("#181818"), width("16ex"), vertical.align("top"))
        object galleryName extends CustomStyle(display("block"), font.weight("bold"), margin.top("0.4ex"), white.space("normal"))
        object galleryInfo extends CustomStyle(display("block"), font.size("80%"), color("#9a9a9a"), white.space("normal"))
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
