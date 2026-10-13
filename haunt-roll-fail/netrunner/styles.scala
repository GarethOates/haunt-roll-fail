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

        Corp --> color("#5aa9e6")
        Runner --> color("#ff7f50")

        Anarch --> color("#ff4500")
        Criminal --> color("#4169e1")
        Shaper --> color("#32cd32")
        HaasBioroid --> color("#9370db")
        Jinteki --> color("#dc143c")
        NBN --> color("#ffd700")
        Weyland --> color("#2e8b57")
        Neutral --> color("#a0a0a0")

        object card extends CustomStyle(display("block"), width("12ex"), border.radius("0.6ex"))
        object cardSmall extends CustomStyle(display("block"), width("8ex"), border.radius("0.4ex"))
        object cardIce extends CustomStyle(display("block"), width("8ex"), border.radius("0.4ex"), transform("rotate(90deg)"), margin("1.5ex 0"))
        object cardTiny extends CustomStyle(display("block"), width("5ex"), border.radius("0.3ex"))
        object artwork extends CustomStyle(max.height("90vh"), max.width("90vw"), border.radius("1.5ex"))

        object slot extends CustomStyle(display("inline-block"), margin("0.3ex"), vertical.align("top"), text.align("center"), position("relative"), font.size("80%"))
        object iceSlot extends CustomStyle(display("inline-block"), margin("0.3ex 0.6ex"), vertical.align("middle"), text.align("center"), position("relative"), font.size("80%"), width("8ex"))
        object server extends CustomStyle(display("inline-block"), margin("0.4ex"), vertical.align("top"), text.align("center"), border.style("solid"), border.width("1px"), border.color("#444444"), border.radius("0.8ex"), padding("0.4ex"), min.width("12ex"))
        object serverRun extends CustomStyle(border.color("#ff7f50"), border.width("2px"))
        object serverName extends CustomStyle(font.size("80%"), color("#a0a0a0"))
        object row extends CustomStyle(display("flex"), flex.wrap("wrap"), justify.content("center"), align.items("flex-start"))
        object rowStart extends CustomStyle(display("flex"), flex.wrap("wrap"), justify.content("flex-start"), align.items("flex-start"))
        object counter extends CustomStyle(font.size("85%"), color("#d3d3d3"), background.color("#222222cc"), border.radius("0.6ex"), padding("0 0.4ex"), display("inline-block"), margin("0.1ex"))
        object counterAdv extends CustomStyle(color("#ffd700"))
        object counterVirus extends CustomStyle(color("#32cd32"))
        object counterCredit extends CustomStyle(color("#5aa9e6"))
        object counterPower extends CustomStyle(color("#ff4500"))
        object name extends CustomStyle(font.weight("bold"), font.size("110%"))
        object stat extends CustomStyle(display("inline-block"), margin("0 0.8ex"), white.space("nowrap"))
        object statLine extends CustomStyle(display("flex"), flex.wrap("wrap"), justify.content("center"))
        object dim extends CustomStyle(color("#808080"))
        object status extends CustomStyle(
            text.align("center"),
            font.size("90%"),
            overflow.x("hidden"),
            overflow.y("auto"),
            padding("0.5ex")
        )
        object hand extends CustomStyle(display("flex"), flex.wrap("wrap"), justify.content("center"), align.items("flex-start"))
        object cardText extends CustomStyle(white.space("pre-wrap"), text.align("left"), max.width("60ex"), font.size("90%"), padding("1ex"))
        object textarea extends CustomStyle(width("60ex"), height("40ex"), font.size("90%"), color("#d3d3d3"), background.color("#111111"))
        object selected extends CustomStyle(outline.color("#ffffff"), outline.style("solid"), outline.width("0.3vmin"))
        object facedown extends CustomStyle(filter("brightness(0.8)"))
        object rezzedMark extends CustomStyle(position("absolute"), top("0"), left("0"), font.size("70%"), background.color("#000000aa"), padding("0 0.3ex"), border.radius("0.4ex"))
        object subBroken extends CustomStyle(color("#808080"), text.decoration.line("line-through"))
        object viewcard extends CustomStyle(display("inline-block"), margin("0.3ex"))
        object clickable extends CustomStyle(cursor("pointer"))
    }

    object borders extends BaseStyleMapping("netrunner-border") {
        import rules._

        Corp --> border.color("#5aa9e6")
        Runner --> border.color("#ff7f50")
    }

    implicit class ElemInt(val n : Int) extends AnyVal {
        def cards = (n != 1).?(n.hl ~ " cards").|("1 card".hl)
        def credits = (n != 1).?(n.hl ~ " credits").|("1 credit".hl)
        def clicks = (n != 1).?(n.hl ~ " clicks").|("1 click".hl)
        def tags = (n != 1).?(n.hl ~ " tags").|("1 tag".hl)
    }
}
