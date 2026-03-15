"""QSS styles for OpenKiwi Companion."""

DARK_THEME = """
QMainWindow {
    background-color: #0D1117;
}
QWidget {
    color: #C9D1D9;
    font-family: "Segoe UI", "Microsoft YaHei", sans-serif;
    font-size: 10pt;
}
QTabWidget::pane {
    border: 1px solid #30363D;
    background-color: #161B22;
}
QTabBar::tab {
    background-color: #0D1117;
    color: #8B949E;
    padding: 8px 16px;
    border: none;
    border-bottom: 2px solid transparent;
}
QTabBar::tab:selected {
    color: #F0F6FC;
    border-bottom: 2px solid #58A6FF;
}
QTabBar::tab:hover {
    color: #C9D1D9;
}
QTextEdit, QPlainTextEdit {
    background-color: #0D1117;
    color: #C9D1D9;
    border: 1px solid #30363D;
    border-radius: 6px;
    padding: 8px;
    selection-background-color: #264F78;
}
QLineEdit {
    background-color: #0D1117;
    color: #C9D1D9;
    border: 1px solid #30363D;
    border-radius: 6px;
    padding: 6px 10px;
}
QLineEdit:focus {
    border-color: #58A6FF;
}
QPushButton {
    background-color: #21262D;
    color: #C9D1D9;
    border: 1px solid #30363D;
    border-radius: 6px;
    padding: 6px 16px;
    font-weight: bold;
}
QPushButton:hover {
    background-color: #30363D;
    border-color: #8B949E;
}
QPushButton:pressed {
    background-color: #161B22;
}
QPushButton#primary {
    background-color: #238636;
    border-color: #2EA043;
    color: white;
}
QPushButton#primary:hover {
    background-color: #2EA043;
}
QPushButton#danger {
    background-color: #DA3633;
    border-color: #F85149;
    color: white;
}
QLabel#status-connected {
    color: #3FB950;
    font-weight: bold;
}
QLabel#status-disconnected {
    color: #8B949E;
}
QLabel#status-error {
    color: #F85149;
    font-weight: bold;
}
QGroupBox {
    border: 1px solid #30363D;
    border-radius: 6px;
    margin-top: 10px;
    padding-top: 16px;
    font-weight: bold;
}
QGroupBox::title {
    subcontrol-origin: margin;
    left: 12px;
    padding: 0 4px;
}
QScrollBar:vertical {
    background: #0D1117;
    width: 8px;
}
QScrollBar::handle:vertical {
    background: #30363D;
    border-radius: 4px;
    min-height: 20px;
}
QScrollBar::handle:vertical:hover {
    background: #484F58;
}
QProgressBar {
    border: 1px solid #30363D;
    border-radius: 4px;
    text-align: center;
    background-color: #0D1117;
}
QProgressBar::chunk {
    background-color: #238636;
    border-radius: 3px;
}
QTreeWidget, QListWidget {
    background-color: #0D1117;
    border: 1px solid #30363D;
    border-radius: 6px;
}
QTreeWidget::item:selected, QListWidget::item:selected {
    background-color: #1F6FEB22;
}
QSplitter::handle {
    background-color: #30363D;
    width: 1px;
}
"""
