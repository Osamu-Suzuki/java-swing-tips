// -*- mode:java; encoding:utf-8 -*-
// vim:set fileencoding=utf-8:
// @homepage@

package example;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.logging.Logger;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class MainPanel extends JPanel {
  private MainPanel() {
    super(new BorderLayout());
    String help = String.join("\n",
        " Start editing: Double-Click, Enter-Key",
        " Commit rename: field-focusLost, Enter-Key",
        "Cancel editing: Esc-Key, title.isEmpty"
    );
    JTabbedPane tabs = new JTabbedPane() {
      private transient TabTitleEditListener listener;
      @Override public void updateUI() {
        removeChangeListener(listener);
        removeMouseListener(listener);
        super.updateUI();
        listener = new TabTitleEditListener(this);
        addChangeListener(listener);
        addMouseListener(listener);
      }
    };
    tabs.addTab("Shortcuts", new JTextArea(help));
    tabs.addTab("JLabel", new JLabel("JLabel"));
    tabs.addTab("JTree", new JScrollPane(new JTree()));
    tabs.addTab("JSplitPane", new JSplitPane());
    add(tabs);
    setPreferredSize(new Dimension(320, 240));
  }

  public static void main(String[] args) {
    EventQueue.invokeLater(MainPanel::createAndShowGui);
  }

  private static void createAndShowGui() {
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (UnsupportedLookAndFeelException ignored) {
      Toolkit.getDefaultToolkit().beep();
    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
      Logger.getGlobal().severe(ex::getMessage);
      return;
    }
    JFrame frame = new JFrame("@title@");
    frame.setMinimumSize(new Dimension(256, 100));
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    frame.getContentPane().add(new MainPanel());
    frame.pack();
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
  }
}

class TabTitleEditListener extends MouseAdapter implements ChangeListener, DocumentListener {
  private static final String START_EDITING = "start-editing";
  private static final String CANCEL_EDITING = "cancel-editing";
  private static final String RENAME_TAB_TITLE = "rename-tab-title";
  private final JTextField editor = new JTextField();
  private final JTabbedPane tabbedPane;
  private int editingIdx = -1;
  private Dimension minSize;
  private Component tabComponent;
  private final Action startEditing = new AbstractAction() {
    @Override public void actionPerformed(ActionEvent e) {
      int idx = tabbedPane.getSelectedIndex();
      // Ignore a restart during editing: getTabComponentAt(idx) would return
      // the editor itself and the original tab component would be lost.
      if (editingIdx < 0 && idx >= 0) {
        startEditingAt(idx);
      }
    }
  };
  private final Action renameTabTitle = new AbstractAction() {
    @Override public void actionPerformed(ActionEvent e) {
      String title = editor.getText().trim();
      if (editingIdx >= 0 && !title.isEmpty()) {
        tabbedPane.setTitleAt(editingIdx, title);
      }
      ActionEvent a = new ActionEvent(
          tabbedPane, ActionEvent.ACTION_PERFORMED, CANCEL_EDITING);
      cancelEditing.actionPerformed(a);
    }
  };
  private final Action cancelEditing = new AbstractAction() {
    @SuppressWarnings("PMD.NullAssignment")
    @Override public void actionPerformed(ActionEvent e) {
      if (editingIdx >= 0) {
        tabbedPane.setTabComponentAt(editingIdx, tabComponent);
        editingIdx = -1;
        minSize = null;
        tabComponent = null;
        editor.setPreferredSize(null);
        tabbedPane.requestFocusInWindow();
      }
    }
  };

  protected TabTitleEditListener(JTabbedPane tabbedPane) {
    super();
    this.tabbedPane = tabbedPane;
    editor.setBorder(BorderFactory.createEmptyBorder());
    editor.addFocusListener(new FocusAdapter() {
      @Override public void focusLost(FocusEvent e) {
        ActionEvent a = new ActionEvent(
            tabbedPane, ActionEvent.ACTION_PERFORMED, RENAME_TAB_TITLE);
        renameTabTitle.actionPerformed(a);
      }
    });
    editor.getDocument().addDocumentListener(this);

    KeyStroke enterKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
    InputMap im = editor.getInputMap(JComponent.WHEN_FOCUSED);
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), CANCEL_EDITING);
    im.put(enterKey, RENAME_TAB_TITLE);

    ActionMap am = editor.getActionMap();
    am.put(CANCEL_EDITING, cancelEditing);
    am.put(RENAME_TAB_TITLE, renameTabTitle);

    tabbedPane.getInputMap(JComponent.WHEN_FOCUSED).put(enterKey, START_EDITING);
    tabbedPane.getActionMap().put(START_EDITING, startEditing);
  }

  @Override public void stateChanged(ChangeEvent e) {
    ActionEvent a = new ActionEvent(
        tabbedPane, ActionEvent.ACTION_PERFORMED, RENAME_TAB_TITLE);
    renameTabTitle.actionPerformed(a);
  }

  @Override public void insertUpdate(DocumentEvent e) {
    updateTabSize();
  }

  @Override public void removeUpdate(DocumentEvent e) {
    updateTabSize();
  }

  @Override public void changedUpdate(DocumentEvent e) {
    /* not needed */
  }

  @Override public void mouseClicked(MouseEvent e) {
    int idx = tabbedPane.indexAtLocation(e.getX(), e.getY());
    boolean isDoubleClick = e.getClickCount() >= 2;
    if (isDoubleClick && idx >= 0 && idx == tabbedPane.getSelectedIndex()) {
      ActionEvent a = new ActionEvent(tabbedPane, ActionEvent.ACTION_PERFORMED, START_EDITING);
      startEditing.actionPerformed(a);
    } else {
      ActionEvent a = new ActionEvent(
          tabbedPane, ActionEvent.ACTION_PERFORMED, RENAME_TAB_TITLE);
      renameTabTitle.actionPerformed(a);
    }
  }

  protected void startEditingAt(int index) {
    editingIdx = index;
    tabComponent = tabbedPane.getTabComponentAt(index);
    tabbedPane.setTabComponentAt(index, editor);
    // updateTabSize() called from setText(...) does nothing while minSize is null
    editor.setPreferredSize(null);
    editor.setText(tabbedPane.getTitleAt(index));
    minSize = editor.getPreferredSize();
    editor.selectAll();
    editor.requestFocusInWindow();
  }

  protected void updateTabSize() {
    if (minSize != null) {
      // Grow to fit the text, but never shrink below the initial title width
      editor.setPreferredSize(null);
      Dimension d = editor.getPreferredSize();
      d.width = Math.max(d.width, minSize.width);
      editor.setPreferredSize(d);
      tabbedPane.revalidate();
    }
  }
}
