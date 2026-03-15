"""OpenKiwi Companion PC - Desktop application for connecting to OpenKiwi Agent."""

import sys
import asyncio
import logging

from PyQt6.QtWidgets import QApplication
from PyQt6.QtGui import QFont

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(name)s] %(levelname)s: %(message)s")


def main():
    app = QApplication(sys.argv)
    app.setApplicationName("OpenKiwi Companion")
    app.setOrganizationName("Orizon")

    font = QFont("Microsoft YaHei", 10)
    app.setFont(font)

    from ui.styles import DARK_THEME
    app.setStyleSheet(DARK_THEME)

    try:
        import qasync
        loop = qasync.QEventLoop(app)
        asyncio.set_event_loop(loop)

        from ui.main_window import MainWindow
        window = MainWindow()
        window.show()

        with loop:
            loop.run_forever()
    except ImportError:
        logging.warning("qasync not installed, falling back to synchronous mode")
        logging.info("Install qasync for async support: pip install qasync")

        from ui.main_window import MainWindow
        window = MainWindow()
        window.show()

        sys.exit(app.exec())


if __name__ == "__main__":
    main()
