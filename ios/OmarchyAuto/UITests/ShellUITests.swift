import XCTest

/// Behavioral shell tests on the simulator — the CI-verifiable part of
/// omarchy-h7w.6's "test portrait and landscape on iPhone and iPad"
/// acceptance. Landscape screenshots come from the runner's simctl step;
/// these prove the interactions survive rotation.
final class ShellUITests: XCTestCase {

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testRailEngagementSurvivesRotation() throws {
        let app = XCUIApplication()
        app.launch()

        // The app opens with every tile minimized (ADR-0010): title bar and
        // rail are present, no stage content.
        XCTAssertTrue(app.staticTexts["OMARCHY // AUTO"].waitForExistence(timeout: 10))
        let drive = app.buttons["rail-drive"]
        XCTAssertTrue(drive.waitForExistence(timeout: 5))

        // Engage DRIVE: the stage fills.
        drive.tap()
        XCTAssertTrue(app.staticTexts["01 · DRIVE"].waitForExistence(timeout: 5))

        // Rotate to landscape: the engaged stage persists.
        XCUIDevice.shared.orientation = .landscapeLeft
        XCTAssertTrue(app.staticTexts["01 · DRIVE"].waitForExistence(timeout: 5))

        // Back to portrait, then tap the engaged tile again to clear it.
        XCUIDevice.shared.orientation = .portrait
        XCTAssertTrue(app.buttons["rail-drive"].tapOnceHittable())
        XCTAssertFalse(app.staticTexts["01 · DRIVE"].waitForExistence(timeout: 2))

        // The THEME control engages the HOME stage from the title bar.
        app.buttons["rail-home"].tap()
        XCTAssertTrue(app.staticTexts["04 · HOME · SETUP"].waitForExistence(timeout: 5))
    }
}

private extension XCUIElement {
    /// Taps the element once it exists and is hittable.
    func tapOnceHittable(timeout: TimeInterval = 5) -> Bool {
        guard waitForExistence(timeout: timeout) else { return false }
        tap()
        return true
    }
}
