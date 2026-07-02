import SwiftUI
import WebKit

// ward-pillcheck PWA wrap — parity with android PillCheckScreen. ?embed=1 hides the PWA's
// install/open-in-browser banners; external origins (Teams handover 등) hand off to the OS.
private let pillCheckURL = URL(string: "https://yuangunn.github.io/ward-pillcheck/?embed=1")!
private let pillCheckHost = "yuangunn.github.io"

// Same defensive height pin as Android: %/dvh chains can collapse to 0 in embedded webviews.
private let heightFixJS = """
(function(){if(window.__ndFix)return;window.__ndFix=1;function f(){var h=window.innerHeight+'px';
[document.documentElement,document.body,document.getElementById('root')].forEach(function(e){if(e)e.style.setProperty('height',h,'important')})}
f();window.addEventListener('resize',f)})()
"""

/// Keeps the WKWebView (and the SPA's state) alive across tab switches.
final class PillCheckStore: NSObject, ObservableObject, WKNavigationDelegate {
    static let shared = PillCheckStore()
    @Published var loading = true
    @Published var failed = false
    lazy var webView: WKWebView = {
        let cfg = WKWebViewConfiguration()
        cfg.userContentController.addUserScript(
            WKUserScript(source: heightFixJS, injectionTime: .atDocumentEnd, forMainFrameOnly: true))
        let wv = WKWebView(frame: .zero, configuration: cfg)
        wv.navigationDelegate = self
        wv.allowsBackForwardNavigationGestures = true
        wv.load(URLRequest(url: pillCheckURL))
        return wv
    }()

    func reload() {
        failed = false
        if webView.url == nil { webView.load(URLRequest(url: pillCheckURL)) } else { webView.reload() }
    }

    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        if let url = navigationAction.request.url, let host = url.host, host != pillCheckHost,
           url.scheme != "about" {
            UIApplication.shared.open(url)   // Teams 딥링크·외부 링크는 OS로
            decisionHandler(.cancel)
            return
        }
        decisionHandler(.allow)
    }
    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        loading = true; failed = false
    }
    func webView(_ webView: WKWebView, didCommit navigation: WKNavigation!) { loading = false }
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) { loading = false }
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        loading = false; failed = true
    }
}

private struct PillWebView: UIViewRepresentable {
    func makeUIView(context: Context) -> WKWebView { PillCheckStore.shared.webView }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}

struct PillCheckView: View {
    @Environment(\.colorScheme) private var scheme
    @ObservedObject private var store = PillCheckStore.shared

    var body: some View {
        let c = NurseColors.of(scheme)
        VStack(spacing: 0) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("지참약 식별").font(.system(size: 24, weight: .heavy)).foregroundStyle(c.text)
                    Text("환자 지참약 낱알 검색").font(.system(size: 13, weight: .semibold)).foregroundStyle(c.sub)
                }
                Spacer()
                Button { store.reload() } label: {
                    Image(systemName: "arrow.clockwise")
                        .font(.system(size: 15, weight: .semibold)).foregroundStyle(c.sub)
                        .frame(width: 38, height: 38)
                        .background(c.cardBg, in: Circle())
                        .overlay(Circle().stroke(c.cardBorder, lineWidth: 1))
                }
            }
            .padding(.init(top: 58, leading: 20, bottom: 12, trailing: 20))

            ZStack {
                PillWebView()
                if store.loading { c.bg.overlay(ProgressView().tint(Color(hex: "#3182F6"))) }
                if store.failed {
                    c.bg.overlay {
                        VStack(spacing: 6) {
                            Text("연결할 수 없어요").font(.system(size: 15, weight: .heavy)).foregroundStyle(c.text)
                            Text("네트워크 연결을 확인한 뒤 다시 시도해 주세요.")
                                .font(.system(size: 13, weight: .semibold)).foregroundStyle(c.sub)
                            Button { store.reload() } label: {
                                Text("다시 시도").font(.system(size: 15, weight: .semibold)).foregroundStyle(.white)
                                    .padding(.horizontal, 26).padding(.vertical, 13)
                                    .background(Duty.brandGradient, in: RoundedRectangle(cornerRadius: 14))
                            }
                            .padding(.top, 12)
                        }
                    }
                }
            }
        }
        .background(c.bg)
        .ignoresSafeArea(edges: .top)
    }
}
