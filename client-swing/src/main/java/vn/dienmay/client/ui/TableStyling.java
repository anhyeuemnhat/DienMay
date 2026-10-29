package vn.dienmay.client.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.function.BiFunction;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

/** Opaque, wrapping cells; all columns remain reachable using the table's horizontal scrollbar. */
public final class TableStyling {
  private TableStyling() {}

  public static void install(JTable table, BiFunction<Integer, Object, Object> format) {
    table.setFont(Ui.TEXT_FONT);
    table.setOpaque(true);
    table.setBackground(Color.WHITE);
    table.setForeground(Ui.TEXT);
    table.setSelectionBackground(Ui.SELECTION);
    table.setSelectionForeground(Color.WHITE);
    table.setGridColor(new Color(197, 181, 188));
    table.setShowGrid(true);
    table.setIntercellSpacing(new Dimension(1, 1));
    table.setRowHeight(38);
    table.setDefaultRenderer(Object.class, new WrappingCell(format));
    table.getTableHeader().setOpaque(true);
    table.getTableHeader().setBackground(Ui.HEADER);
    table.getTableHeader().setForeground(Color.WHITE);
    table.getTableHeader().setFont(Ui.TEXT_FONT.deriveFont(Font.BOLD));
    table.getTableHeader().setDefaultRenderer(new Header());
    table.getTableHeader().setPreferredSize(new Dimension(0, 42));
    Timer resize = new Timer(100, event -> fitRows(table));
    resize.setRepeats(false);
    table.putClientProperty("rowHeightTimer", resize);
    table.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
      @Override
      public void columnAdded(TableColumnModelEvent event) {
        resize.restart();
      }

      @Override
      public void columnRemoved(TableColumnModelEvent event) {
        resize.restart();
      }

      @Override
      public void columnMoved(TableColumnModelEvent event) {
        resize.restart();
      }

      @Override
      public void columnMarginChanged(ChangeEvent event) {
        resize.restart();
      }

      @Override
      public void columnSelectionChanged(ListSelectionEvent event) {}
    });
    table.getRowSorter().addRowSorterListener(event -> resize.restart());
  }

  public static void fitColumns(JTable table) {
    for (int column = 0; column < table.getColumnCount(); column++) {
      var definition = table.getColumnModel().getColumn(column);
      int heading = table.getTableHeader().getFontMetrics(table.getTableHeader().getFont())
          .stringWidth(String.valueOf(definition.getHeaderValue())) + 34;
      int width = Math.max(85, heading);
      for (int row = 0; row < table.getRowCount(); row++) {
        Component cell = table.getCellRenderer(row, column).getTableCellRendererComponent(table,
            table.getValueAt(row, column), false, false, row, column);
        if (cell instanceof JTextArea area) {
          for (String line : area.getText().split("\\R", -1)) {
            width = Math.max(width, Math.min(360, area.getFontMetrics(area.getFont()).stringWidth(line) + 26));
          }
        }
      }
      definition.setMinWidth(Math.max(65, heading));
      definition.setPreferredWidth(width);
      definition.setWidth(width);
    }
    fitRows(table);
  }

  public static void fitRows(JTable table) {
    for (int row = 0; row < table.getRowCount(); row++) {
      int height = 38;
      for (int column = 0; column < table.getColumnCount(); column++) {
        Component cell = table.getCellRenderer(row, column).getTableCellRendererComponent(table,
            table.getValueAt(row, column), false, false, row, column);
        cell.setSize(Math.max(1, table.getColumnModel().getColumn(column).getWidth() - 1), Short.MAX_VALUE);
        height = Math.max(height, cell.getPreferredSize().height + 1);
      }
      if (table.getRowHeight(row) != height) {
        table.setRowHeight(row, height);
      }
    }
  }

  @SuppressWarnings("serial") // View state is never Java-serialized.
  private static final class WrappingCell extends JTextArea implements TableCellRenderer {
    private final BiFunction<Integer, Object, Object> format;

    private WrappingCell(BiFunction<Integer, Object, Object> format) {
      this.format = format;
      setOpaque(true);
      setEditable(false);
      setLineWrap(true);
      setWrapStyleWord(true);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
        boolean focus, int row, int column) {
      Object shown = format.apply(table.convertColumnIndexToModel(column), value);
      setText(shown == null ? "" : String.valueOf(shown));
      setFont(table.getFont());
      setForeground(selected ? Color.WHITE : Ui.TEXT);
      setBackground(selected ? Ui.SELECTION : row % 2 == 0 ? Color.WHITE : Ui.ALTERNATE_ROW);
      setBorder(focus ? BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(selected ? Color.WHITE :
          Ui.FOCUS, 2),
          BorderFactory.createEmptyBorder(5, 8, 5, 8)) : BorderFactory.createEmptyBorder(7, 10, 7, 10));
      setToolTipText(shown == null ? null : String.valueOf(shown));
      return this;
    }
  }

  @SuppressWarnings("serial") // View state is never Java-serialized.
  private static final class Header extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
        boolean focus, int row, int column) {
      super.getTableCellRendererComponent(table, value, false, false, row, column);
      setOpaque(true);
      setForeground(Color.WHITE);
      setBackground(Ui.HEADER);
      setFont(Ui.TEXT_FONT.deriveFont(Font.BOLD));
      setHorizontalAlignment(SwingConstants.LEADING);
      setHorizontalTextPosition(SwingConstants.LEADING);
      setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 1,
          Ui.CONTROL_BORDER), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
      setToolTipText(String.valueOf(value));
      setIcon(null);
      for (var key : table.getRowSorter().getSortKeys()) {
        if (key.getColumn() == table.convertColumnIndexToModel(column)) {
          setIcon(UIManager.getIcon(key.getSortOrder() == SortOrder.ASCENDING
              ? "Table.ascendingSortIcon" : "Table.descendingSortIcon"));
          break;
        }
      }
      return this;
    }
  }
}
