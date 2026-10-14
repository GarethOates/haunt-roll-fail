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
        object bigStat extends CustomStyle(display("inline-flex"), flex.direction("column"), align.items("center"), justify.content("center"), min.width("4.4ex"), padding.ver("0.15ex"), padding.hor("0.55ex"), margin.ver("0.15ex"), margin.hor("0.18ex"), border.radius("0.8ex"), border.style("solid"), border.width("0.25ex"), border.color("transparent"), line.height("1"), white.space("nowrap"))
        object bigValue extends CustomStyle(font.size("220%"), font.weight("bold"))
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
