package GUI;
import javax.swing.*;import javax.swing.table.*;import java.awt.*;import java.util.*;import java.util.List;
/** Small shared read-only table shell; domain actions remain explicit in their screens. */
public class RowsFrame extends JFrame {
    protected final JTextField search=new JTextField(22);
    protected final JPanel toolbar=new JPanel(new FlowLayout(FlowLayout.LEFT));
    protected final JTable table=new JTable(Ui.model());
    protected final JLabel status=new JLabel(" ");
    private List<LinkedHashMap<String,Object>> rows=List.of();
    public RowsFrame(String title){super(title);setDefaultCloseOperation(DISPOSE_ON_CLOSE);toolbar.add(new JLabel("Từ khóa:"));toolbar.add(search);add(toolbar,BorderLayout.NORTH);add(new JScrollPane(table));add(status,BorderLayout.SOUTH);Ui.setupTable(table);setSize(1120,650);setLocationRelativeTo(null);Ui.decorate(this);}
    public void rows(List<LinkedHashMap<String,Object>> values){
        rows=List.copyOf(values);DefaultTableModel m=(DefaultTableModel)table.getModel();m.setRowCount(0);m.setColumnCount(0);
        if(values.isEmpty()){status.setText("Không có dữ liệu phù hợp.");return;}
        for(String k:values.get(0).keySet())m.addColumn(k.replace(" UTC"," (VN)"));
        for(Map<String,Object> row:values)m.addRow(row.values().toArray());status.setText("Hiển thị "+values.size()+" dòng. Tìm hẹp hơn khi đạt giới hạn 500/1.000 dòng.");
    }
    protected Map<String,Object> selected(){int row=table.getSelectedRow();if(row<0)throw new IllegalArgumentException("Hãy chọn một dòng.");return rows.get(table.convertRowIndexToModel(row));}
    protected long selectedId(){return ((Number)selected().values().iterator().next()).longValue();}
    protected void findAction(Runnable action){toolbar.add(Ui.button("Tìm / tải lại",action));search.addActionListener(e->Ui.safe(action));}
}
