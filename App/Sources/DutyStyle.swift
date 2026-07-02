import SwiftUI

// 1b handoff tokens — kept in lockstep with android ui/DutyStyle.kt + ui/Theme.kt.

enum Duty {
    static let kinds = ["Day", "Mid", "Evening", "Night", "Off"]

    static func color(_ kind: String) -> Color {
        switch kind {
        case "Day": Color(hex: "#3182F6"); case "Mid": Color(hex: "#14B8A6")
        case "Evening": Color(hex: "#F59E0B"); case "Night": Color(hex: "#5B5BD6")
        case "Off": Color(hex: "#94A3B8"); default: Color(hex: "#3182F6")
        }
    }

    static func gradient(_ kind: String) -> LinearGradient {
        let colors: [String] = switch kind {
        case "Day": ["#3182F6", "#5BA0FF"]
        case "Mid": ["#14B8A6", "#4FD1C5"]
        case "Evening": ["#F59E0B", "#FBBF4D"]
        case "Night": ["#5B5BD6", "#8487F0"]
        case "Off": ["#94A3B8", "#B4C0D0"]
        default: ["#3182F6", "#5BA0FF"]
        }
        return LinearGradient(colors: colors.map { Color(hex: $0) },
                              startPoint: .topLeading, endPoint: .bottomTrailing)
    }

    static let brandGradient = LinearGradient(colors: [Color(hex: "#3182F6"), Color(hex: "#5BA0FF")],
                                              startPoint: .topLeading, endPoint: .bottomTrailing)

    static func letter(_ kind: String) -> String {
        switch kind { case "Day": "D"; case "Mid": "M"; case "Evening": "E"
        case "Night": "N"; case "Off": "O"; default: "·" }
    }
    static func short(_ kind: String) -> String {
        switch kind { case "Day": "데이"; case "Mid": "미드"; case "Evening": "이브"
        case "Night": "나잇"; case "Off": "오프"; default: "" }
    }
    static func ko(_ kind: String) -> String {
        switch kind { case "Day": "데이"; case "Mid": "미드"; case "Evening": "이브닝"
        case "Night": "나이트"; case "Off": "휴무"; default: "" }
    }

    struct Sky {
        let grad: LinearGradient
        let greeting: String
        var restIcon = ""
        var restTitle = ""
        var restSub = ""
    }

    private static func lg(_ hexes: [String]) -> LinearGradient {
        LinearGradient(colors: hexes.map { Color(hex: $0) }, startPoint: .top, endPoint: .bottom)
    }

    static func sky(_ kind: String) -> Sky {
        switch kind {
        case "Day": Sky(grad: lg(["#3E86F4", "#5C9BF6", "#8FBCF2"]), greeting: "좋은 아침이에요 ☀️")
        case "Mid": Sky(grad: lg(["#159FC4", "#37B8CE", "#86DBDE"]), greeting: "미드 근무 화이팅이에요 🌤️")
        case "Evening": Sky(grad: lg(["#FF9E57", "#FB7268", "#C95E94"]), greeting: "오후도 화이팅이에요 🌇")
        case "Night": Sky(grad: lg(["#241C57", "#3D2F86", "#5E4DBC"]), greeting: "오늘 밤도 안전 근무해요 🌙")
        case "Off": Sky(grad: lg(["#5B9E86", "#86C3A2", "#BFE3C8"]), greeting: "푹 쉬는 날이에요 ☕",
                        restIcon: "☕", restTitle: "오늘은 휴무예요",
                        restSub: "알람도 체크리스트도 없어요.\n충분히 쉬고 다음 근무 때 만나요!")
        default: Sky(grad: lg(["#8C8AA6", "#AEA9C2", "#CFCBDD"]), greeting: "오늘은 근무가 없어요",
                     restIcon: "🗓", restTitle: "근무가 배정되지 않았어요",
                     restSub: "근무표에서 오늘 날짜를 눌러\n근무를 배정해 보세요.")
        }
    }

    static let gold = Color(hex: "#EAB308")
    static let goldInk = Color(hex: "#B4820A")
    static let success = Color(hex: "#22C55E")
    static let danger = Color(hex: "#EF4444")
}

// MARK: - Warm palette (당근 fintech, light/dark)

struct NurseColors {
    let bg, text, sub, faint, cardBg, cardBorder, chipBg, chipText, track,
        sheetBg, grab, clearBg, inputBg, inputBorder: Color

    static let light = NurseColors(
        bg: Color(hex: "#FBF7F0"), text: Color(hex: "#241D13"), sub: Color(hex: "#8A7D6A"),
        faint: Color(hex: "#B6A992"), cardBg: Color(hex: "#FFFDF9"), cardBorder: Color(hex: "#EFE7D9"),
        chipBg: Color(hex: "#F3ECE0"), chipText: Color(hex: "#8A7D6A"), track: Color(hex: "#F0E9DC"),
        sheetBg: Color(hex: "#FFFDF9"), grab: Color(hex: "#E2D8C7"), clearBg: Color(hex: "#FBECEA"),
        inputBg: Color(hex: "#F7F1E7"), inputBorder: Color(hex: "#E8DECB"))

    static let dark = NurseColors(
        bg: Color(hex: "#15110B"), text: Color(hex: "#F4EEE2"), sub: Color(hex: "#9C9081"),
        faint: Color(hex: "#6E6555"), cardBg: Color(hex: "#211B12"), cardBorder: Color(hex: "#2E2619"),
        chipBg: Color(hex: "#2A2317"), chipText: Color(hex: "#B7AB98"), track: Color(hex: "#2A2317"),
        sheetBg: Color(hex: "#1C1710"), grab: Color(hex: "#39301F"), clearBg: Color(hex: "#2A1614"),
        inputBg: Color(hex: "#171208"), inputBorder: Color(hex: "#33291A"))

    static func of(_ scheme: ColorScheme) -> NurseColors { scheme == .dark ? .dark : .light }
}

// MARK: - Shared building blocks

struct SoftCard<Content: View>: View {
    @Environment(\.colorScheme) private var scheme
    var pad: CGFloat = 16
    @ViewBuilder var content: Content

    var body: some View {
        let c = NurseColors.of(scheme)
        VStack(alignment: .leading, spacing: 0) { content }
            .padding(pad)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(c.cardBg, in: RoundedRectangle(cornerRadius: 20))
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(c.cardBorder, lineWidth: 1))
    }
}

struct DutyAvatar: View {
    let kind: String
    var size: CGFloat = 46
    var radius: CGFloat = 14

    var body: some View {
        Text(Duty.letter(kind))
            .font(.system(size: size * 0.38, weight: .heavy))
            .foregroundStyle(.white)
            .frame(width: size, height: size)
            .background(Duty.gradient(kind), in: RoundedRectangle(cornerRadius: radius))
    }
}
