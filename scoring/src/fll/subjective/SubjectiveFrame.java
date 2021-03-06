/*
 * Copyright (c) 2000-2002 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */
package fll.subjective;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.prefs.Preferences;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultCellEditor;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyledDocument;

import net.mtu.eggplant.util.BasicFileFilter;
import net.mtu.eggplant.util.gui.GraphicsUtils;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import fll.Utilities;
import fll.util.FLLRuntimeException;
import fll.util.LogUtils;
import fll.xml.AbstractGoal;
import fll.xml.ChallengeDescription;
import fll.xml.ChallengeParser;
import fll.xml.EnumeratedValue;
import fll.xml.ScoreCategory;
import fll.xml.XMLUtils;

/**
 * Application to enter subjective scores with
 */
public final class SubjectiveFrame extends JFrame {

  private static final Logger LOGGER = LogUtils.getLogger();

  public static void main(final String[] args) {
    LogUtils.initializeLogging();

    // Use cross platform look and feel so that things look right all of the
    // time
    try {
      UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
    } catch (final ClassNotFoundException e) {
      LOGGER.warn("Could not find cross platform look and feel class", e);
    } catch (final InstantiationException e) {
      LOGGER.warn("Could not instantiate cross platform look and feel class", e);
    } catch (final IllegalAccessException e) {
      LOGGER.warn("Error loading cross platform look and feel", e);
    } catch (final UnsupportedLookAndFeelException e) {
      LOGGER.warn("Cross platform look and feel unsupported?", e);
    }

    try {
      final SubjectiveFrame frame = new SubjectiveFrame();
      GraphicsUtils.centerWindow(frame);
      frame.setVisible(true);
      frame.promptForFile();

    } catch (final Throwable e) {
      JOptionPane.showMessageDialog(null, "Unexpected error: "
          + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
      LOGGER.fatal("Unexpected error", e);
      System.exit(1);
    }
  }

  /**
   * Create a window to edit subjective scores.
   */
  public SubjectiveFrame() {
    super("Subjective Score Entry");

    getContentPane().setLayout(new BorderLayout());

    final JPanel topPanel = new JPanel();
    getContentPane().add(topPanel, BorderLayout.NORTH);

    final JButton quitButton = new JButton("Quit");
    topPanel.add(quitButton);
    quitButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent ae) {
        quit();
      }
    });

    final JButton saveButton = new JButton("Save");
    topPanel.add(saveButton);
    saveButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent ae) {
        try {
          save();
        } catch (final IOException ioe) {
          JOptionPane.showMessageDialog(null, "Error writing to data file: "
              + ioe.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }

      }
    });

    final JButton summaryButton = new JButton("Summary");
    topPanel.add(summaryButton);
    summaryButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent ae) {
        final SummaryDialog dialog = new SummaryDialog(SubjectiveFrame.this);

        dialog.pack();
        dialog.setVisible(true);
      }
    });

    final JButton compareButton = new JButton("Compare Scores");
    topPanel.add(compareButton);
    compareButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent ae) {
        final File compareFile = chooseSubjectiveFile("Choose the file to compare with");
        if (null != compareFile) {
          try {
            save();
          } catch (final IOException ioe) {
            JOptionPane.showMessageDialog(null, "Error writing to data file: "
                + ioe.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
          }

          try {
            final Collection<SubjectiveScoreDifference> diffs = SubjectiveUtils.compareSubjectiveFiles(getFile(),
                                                                                                       compareFile);
            if (null == diffs) {
              JOptionPane.showMessageDialog(null, "Challenge descriptors are different, comparison failed", "Error",
                                            JOptionPane.ERROR_MESSAGE);

            } else if (!diffs.isEmpty()) {
              showDifferencesDialog(diffs);
            } else {
              JOptionPane.showMessageDialog(null, "No differences found", "No Differences",
                                            JOptionPane.INFORMATION_MESSAGE);

            }
          } catch (final SAXParseException spe) {
            final String errorMessage = String.format("Error parsing file line: %d column: %d%n Message: %s%n This may be caused by using the wrong version of the software attempting to parse a file that is not subjective data.",
                                                      spe.getLineNumber(), spe.getColumnNumber(), spe.getMessage());
            LOGGER.error(errorMessage, spe);
            JOptionPane.showMessageDialog(null, errorMessage, "Error", JOptionPane.ERROR_MESSAGE);
          } catch (final SAXException se) {
            final String errorMessage = "The subjective scores file was found to be invalid, check that you are parsing a subjective scores file and not something else";
            LOGGER.error(errorMessage, se);
            JOptionPane.showMessageDialog(null, errorMessage, "Error", JOptionPane.ERROR_MESSAGE);
          } catch (final IOException e) {
            LOGGER.error("Error reading compare file", e);
            JOptionPane.showMessageDialog(null, "Error reading compare file: "
                + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);

          }
        }
      }
    });

    tabbedPane = new JTabbedPane();
    getContentPane().add(tabbedPane, BorderLayout.CENTER);

    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(final WindowEvent e) {
        quit();
      }
    });
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

    pack();
  }

  /**
   * Prompt the user for a file to load. Calls exit on error.
   * 
   * @throws IOException
   */
  @SuppressFBWarnings(value = "DM_EXIT", justification = "If no file is chosen we cannot continue")
  public void promptForFile() throws IOException {
    final File file = chooseSubjectiveFile("Please choose the subjective data file");
    try {
      if (null != file) {
        load(file);
      } else {
        System.exit(0);
      }
    } catch (final IOException ioe) {
      JOptionPane.showMessageDialog(null, "Error reading data file: "
          + file.getAbsolutePath() + " - " + ioe.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
      LOGGER.fatal("Error reading datafile: "
          + file.getAbsolutePath(), ioe);
      System.exit(1);
    }
  }

  /**
   * Load data. Meant for testing. Most users should use #promptForFile().
   * 
   * @param file where to read the data in from and where to save data to
   * @throws IOException
   */
  public void load(final File file) throws IOException {
    _file = file;

    ZipFile zipfile = null;
    try {
      zipfile = new ZipFile(file);
      final ZipEntry challengeEntry = zipfile.getEntry("challenge.xml");
      if (null == challengeEntry) {
        throw new FLLRuntimeException(
                                      "Unable to find challenge descriptor in file, you probably choose the wrong file or it is corrupted");
      }
      final InputStream challengeStream = zipfile.getInputStream(challengeEntry);
      _challengeDocument = ChallengeParser.parse(new InputStreamReader(challengeStream, Utilities.DEFAULT_CHARSET));
      challengeStream.close();

      _challengeDescription = new ChallengeDescription(_challengeDocument.getDocumentElement());

      final ZipEntry scoreEntry = zipfile.getEntry("score.xml");
      if (null == scoreEntry) {
        throw new FLLRuntimeException(
                                      "Unable to find score data in file, you probably choose the wrong file or it is corrupted");
      }
      final InputStream scoreStream = zipfile.getInputStream(scoreEntry);
      _scoreDocument = XMLUtils.parseXMLDocument(scoreStream);
      scoreStream.close();
    } catch (final SAXParseException spe) {
      final String errorMessage = String.format("Error parsing file line: %d column: %d%n Message: %s%n This may be caused by using the wrong version of the software attempting to parse a file that is not subjective data.",
                                                spe.getLineNumber(), spe.getColumnNumber(), spe.getMessage());
      throw new FLLRuntimeException(errorMessage, spe);
    } catch (final SAXException se) {
      final String errorMessage = "The subjective scores file was found to be invalid, check that you are parsing a subjective scores file and not something else";
      throw new FLLRuntimeException(errorMessage, se);
    } finally {
      if (null != zipfile) {
        try {
          zipfile.close();
        } catch (final IOException e) {
          LOGGER.debug("Error closing zipfile", e);
        }
      }
    }

    for (final ScoreCategory subjectiveCategory : getChallengeDescription().getSubjectiveCategories()) {
      createSubjectiveTable(tabbedPane, subjectiveCategory);
    }

    // get the name and location of the tournament
    final Element top = _scoreDocument.getDocumentElement();
    final String tournamentName = top.getAttribute("tournamentName");
    if (top.hasAttribute("tournamentLocation")) {
      final String tournamentLocation = top.getAttribute("tournamentName");
      setTitle(String.format("Subjective Score Entry - %s @ %s", tournamentName, tournamentLocation));
    } else {
      setTitle(String.format("Subjective Score Entry - %s", tournamentName));
    }

    pack();
  }

  private void createSubjectiveTable(final JTabbedPane tabbedPane,
                                     final ScoreCategory subjectiveCategory) {
    final SubjectiveTableModel tableModel = new SubjectiveTableModel(_scoreDocument, subjectiveCategory);
    final JTable table = new JTable(tableModel);

    // Make grid lines black (needed for Mac)
    table.setGridColor(Color.BLACK);

    // auto table sorter
    table.setAutoCreateRowSorter(true);

    final String title = subjectiveCategory.getTitle();
    _tables.put(title, table);
    final JScrollPane tableScroller = new JScrollPane(table);
    tableScroller.setPreferredSize(new Dimension(640, 480));
    tabbedPane.addTab(title, tableScroller);

    table.setSelectionBackground(Color.YELLOW);

    setupTabReturnBehavior(table);

    int goalIndex = 0;
    for (final AbstractGoal goal : subjectiveCategory.getGoals()) {
      final TableColumn column = table.getColumnModel().getColumn(goalIndex
          + SubjectiveTableModel.NUM_COLUMNS_LEFT_OF_SCORES);
      if (goal.isEnumerated()) {
        final Vector<String> posValues = new Vector<String>();
        posValues.add("");
        for (final EnumeratedValue posValue : goal.getValues()) {
          posValues.add(posValue.getTitle());
        }

        column.setCellEditor(new DefaultCellEditor(new JComboBox<String>(posValues)));
      } else {
        final JTextField editor = new SelectTextField();
        column.setCellEditor(new DefaultCellEditor(editor));
      }
      ++goalIndex;
    }
  }

  /**
   * Set the tab and return behavior for a table.
   */
  private void setupTabReturnBehavior(final JTable table) {
    final InputMap im = table.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    // Have the enter key work the same as the tab key
    final KeyStroke tab = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0);
    final KeyStroke enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
    im.put(enter, im.get(tab));

    // Override the default tab behavior
    // Tab to the next editable cell. When no editable cells goto next cell.
    final Action oldTabAction = table.getActionMap().get(im.get(tab));
    final Action tabAction = new AbstractAction() {
      public void actionPerformed(final ActionEvent e) {
        oldTabAction.actionPerformed(e);
        final JTable table = (JTable) e.getSource();
        final int rowCount = table.getRowCount();
        final int columnCount = table.getColumnCount();
        int row = table.getSelectedRow();
        int column = table.getSelectedColumn();

        // skip the no show when tabbing
        while (!table.isCellEditable(row, column)
            || table.getColumnClass(column) == Boolean.class) {
          column += 1;

          if (column == columnCount) {
            column = 0;
            row += 1;
          }

          if (row == rowCount) {
            row = 0;
          }

          // Back to where we started, get out.
          if (row == table.getSelectedRow()
              && column == table.getSelectedColumn()) {
            break;
          }
        }

        table.changeSelection(row, column, false, false);
      }
    };
    table.getActionMap().put(im.get(tab), tabAction);
  }

  /**
   * Find tab index for category title.
   * 
   * @param category
   * @return the tab index, -1 on error
   */
  private int getTabIndexForCategory(final String category) {
    final int tabCount = tabbedPane.getTabCount();
    for (int i = 0; i < tabCount; ++i) {
      if (category.equals(tabbedPane.getTitleAt(i))) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Show differences.
   */
  private void showDifferencesDialog(final Collection<SubjectiveScoreDifference> diffs) {
    final SubjectiveDiffTableModel model = new SubjectiveDiffTableModel(diffs);
    final JTable table = new JTable(model);
    table.setGridColor(Color.BLACK);

    table.addMouseListener(new MouseAdapter() {
      public void mouseClicked(final MouseEvent e) {
        if (e.getClickCount() == 2) {
          final JTable target = (JTable) e.getSource();
          final int row = target.getSelectedRow();

          final SubjectiveScoreDifference diff = model.getDiffForRow(row);

          // find correct table
          final String category = diff.getCategory();
          final JTable scoreTable = getTableForTitle(category);
          final int tabIndex = getTabIndexForCategory(category);
          getTabbedPane().setSelectedIndex(tabIndex);

          // get correct row and column
          final SubjectiveTableModel model = (SubjectiveTableModel) scoreTable.getModel();
          final int scoreRow = model.getRowForTeamAndJudge(diff.getTeamNumber(), diff.getJudge());
          final int scoreCol = model.getColForSubcategory(diff.getSubcategory());
          if (scoreRow == -1
              || scoreCol == -1) {
            throw new FLLRuntimeException("Internal error: Cannot find correct row and column for score difference: "
                + diff);
          }

          scoreTable.changeSelection(scoreRow, scoreCol, false, false);
        }
      }
    });

    final JDialog dialog = new JDialog(this, false);
    final Container cpane = dialog.getContentPane();
    cpane.setLayout(new BorderLayout());
    final JScrollPane tableScroller = new JScrollPane(table);
    cpane.add(tableScroller, BorderLayout.CENTER);

    dialog.pack();
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    dialog.setVisible(true);
  }

  /**
   * Prompt the user for a file.
   * 
   * @param title the title on the chooser dialog
   * @return the file if accepted, null if canceled
   */
  private File chooseSubjectiveFile(final String title) {
    final File initialDirectory = getInitialDirectory();
    final JFileChooser fileChooser = new JFileChooser(initialDirectory);
    fileChooser.setDialogTitle(title);
    fileChooser.setFileFilter(new BasicFileFilter("FLL Subjective Data Files", "fll"));
    final int state = fileChooser.showOpenDialog(this);
    if (JFileChooser.APPROVE_OPTION == state) {
      final File file = fileChooser.getSelectedFile();
      setInitialDirectory(file);
      return file;
    } else {
      return null;
    }
  }

  /**
   * Prompt the user with yes/no/cancel. Yes exits and saves, no exits without
   * saving and cancel doesn't quit.
   */
  @SuppressFBWarnings(value = "DM_EXIT", justification = "This is the exit method for the application")
  /* package */void quit() {
    if (validateData()) {

      final int state = JOptionPane.showConfirmDialog(SubjectiveFrame.this,
                                                      "Save data?  Data will be saved in same file as it was read from.",
                                                      "Exit", JOptionPane.YES_NO_CANCEL_OPTION);
      if (JOptionPane.YES_OPTION == state) {
        try {
          save();
          setVisible(false);
          dispose();
        } catch (final IOException ioe) {
          JOptionPane.showMessageDialog(null, "Error writing to data file: "
              + ioe.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }

        System.exit(0);
      } else if (JOptionPane.NO_OPTION == state) {
        setVisible(false);
        dispose();
        System.exit(0);
      }
    }
  }

  /**
   * Make sure the data in the table is valid. This checks to make sure that for
   * all rows, all columns that contain numeric data are actually set, or none
   * of these columns are set in a row. This avoids the case of partial data.
   * This method is fail fast in that it will display a dialog box on the first
   * error it finds.
   * 
   * @return true if everything is ok
   */
  @SuppressFBWarnings(value = "SIC_INNER_SHOULD_BE_STATIC_ANON", justification = "Static inner class to replace anonomous listener isn't worth the confusion of finding the class definition")
  private boolean validateData() {
    stopCellEditors();

    final List<String> warnings = new LinkedList<String>();
    for (final ScoreCategory subjectiveCategory : getChallengeDescription().getSubjectiveCategories()) {
      final String category = subjectiveCategory.getName();
      final String categoryTitle = subjectiveCategory.getTitle();

      final List<AbstractGoal> goals = subjectiveCategory.getGoals();
      final List<Element> scoreElements = SubjectiveTableModel.getScoreElements(_scoreDocument, category);
      for (final Element scoreElement : scoreElements) {
        int numValues = 0;
        for (final AbstractGoal goal : goals) {
          final String goalName = goal.getName();

          final Element subEle = SubjectiveUtils.getSubscoreElement(scoreElement, goalName);
          if (null != subEle) {
            final String value = subEle.getAttribute("value");
            if (!value.isEmpty()) {
              numValues++;
            }
          }
        }
        if (numValues != goals.size()
            && numValues != 0) {
          warnings.add(categoryTitle
              + ": " + scoreElement.getAttribute("teamNumber") + " has too few scores (needs all or none): "
              + numValues);
        }

      }
    }

    if (!warnings.isEmpty()) {
      // join the warnings with carriage returns and display them
      final StyledDocument doc = new DefaultStyledDocument();
      for (final String warning : warnings) {
        try {
          doc.insertString(doc.getLength(), warning
              + "\n", null);
        } catch (final BadLocationException ble) {
          throw new RuntimeException(ble);
        }
      }
      final JDialog dialog = new JDialog(this, "Warnings");
      final Container cpane = dialog.getContentPane();
      cpane.setLayout(new BorderLayout());
      final JButton okButton = new JButton("Ok");
      cpane.add(okButton, BorderLayout.SOUTH);
      okButton.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent ae) {
          dialog.setVisible(false);
          dialog.dispose();
        }
      });
      cpane.add(new JTextPane(doc), BorderLayout.CENTER);
      dialog.pack();
      dialog.setVisible(true);
      return false;
    } else {
      return true;
    }
  }

  /**
   * Stop the cell editors to ensure data is flushed
   */
  private void stopCellEditors() {
    final Iterator<JTable> iter = _tables.values().iterator();
    while (iter.hasNext()) {
      final JTable table = iter.next();
      final int editingColumn = table.getEditingColumn();
      final int editingRow = table.getEditingRow();
      if (editingColumn > -1) {
        final TableCellEditor cellEditor = table.getCellEditor(editingRow, editingColumn);
        if (null != cellEditor) {
          cellEditor.stopCellEditing();
        }
      }
    }
  }

  /**
   * Save out to the same file that things were read in.
   * 
   * @throws IOException if an error occurs writing to the file
   */
  public void save() throws IOException {
    if (validateData()) {

      final ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(_file));
      final Writer writer = new OutputStreamWriter(zipOut, Utilities.DEFAULT_CHARSET);

      zipOut.putNextEntry(new ZipEntry("challenge.xml"));
      XMLUtils.writeXML(_challengeDocument, writer, Utilities.DEFAULT_CHARSET.name());
      zipOut.closeEntry();
      zipOut.putNextEntry(new ZipEntry("score.xml"));
      XMLUtils.writeXML(_scoreDocument, writer, Utilities.DEFAULT_CHARSET.name());
      zipOut.closeEntry();

      zipOut.close();
    }
  }

  /**
   * Set the initial directory preference. This supports opening new file
   * dialogs to a (hopefully) better default in the user's next session.
   * 
   * @param dir the File for the directory in which file dialogs should open
   */
  private static void setInitialDirectory(final File dir) {
    // Store only directories
    final File directory;
    if (dir.isDirectory()) {
      directory = dir;
    } else {
      directory = dir.getParentFile();
    }

    final Preferences preferences = Preferences.userNodeForPackage(SubjectiveFrame.class);
    final String previousPath = preferences.get(INITIAL_DIRECTORY_PREFERENCE_KEY, null);

    if (!directory.toString().equals(previousPath)) {
      preferences.put(INITIAL_DIRECTORY_PREFERENCE_KEY, directory.toString());
    }
  }

  /**
   * Get the initial directory to which file dialogs should open. This supports
   * opening to a better directory across sessions.
   * 
   * @return the File for the initial directory
   */
  private static File getInitialDirectory() {
    final Preferences preferences = Preferences.userNodeForPackage(SubjectiveFrame.class);
    final String path = preferences.get(INITIAL_DIRECTORY_PREFERENCE_KEY, null);

    File dir = null;
    if (null != path) {
      dir = new File(path);
    }
    return dir;
  }

  /**
   * Preferences key for file dialog initial directory
   */
  private static final String INITIAL_DIRECTORY_PREFERENCE_KEY = "InitialDirectory";

  private final Map<String, JTable> _tables = new HashMap<String, JTable>();

  public JTable getTableForTitle(final String title) {
    final JTable table = _tables.get(title);
    if (null == table) {
      return null;
    } else {
      return table;
    }
  }

  /**
   * Get the table model for a given subjective title. Mostly for testing.
   * 
   * @return the table model or null if the specified title is not present
   */
  public TableModel getTableModelForTitle(final String title) {
    final JTable table = getTableForTitle(title);
    if (null == table) {
      return null;
    } else {
      return table.getModel();
    }
  }

  private File _file;

  private File getFile() {
    return _file;
  }

  private ChallengeDescription _challengeDescription;

  /* package */ChallengeDescription getChallengeDescription() {
    return _challengeDescription;
  }

  private Document _challengeDocument;

  private Document _scoreDocument;

  /* package */Document getScoreDocument() {
    return _scoreDocument;
  }

  private final JTabbedPane tabbedPane;

  JTabbedPane getTabbedPane() {
    return tabbedPane;
  }

  /**
   * {@link JTextField} that selects all text when {@link #setText(String)} is
   * called.
   */
  private static final class SelectTextField extends JTextField {
    public SelectTextField() {
      super();
    }

    @Override
    public void setText(final String str) {
      super.setText(str);
      selectAll();
    }
  }
}
