// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "NurseDutyModel",
    platforms: [.iOS(.v18), .watchOS(.v11), .macOS(.v15)],
    products: [
        .library(name: "NurseDutyModel", targets: ["NurseDutyModel"]),
    ],
    targets: [
        // ponytail: Swift 5 language mode — dodges strict-Sendable noise on SwiftData @Model
        // classes (not Sendable by design). Flip to .v6 when we actually add cross-actor code.
        .target(
            name: "NurseDutyModel",
            swiftSettings: [.swiftLanguageMode(.v5)]
        ),
        .testTarget(
            name: "NurseDutyModelTests",
            dependencies: ["NurseDutyModel"],
            swiftSettings: [.swiftLanguageMode(.v5)]
        ),
    ]
)
