package GUI;
import Code.*;
import Service.*;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;

/** Upgraded controller; original NetBeans generated layout retained. */
public class MakeSaleUI extends javax.swing.JFrame {

    private final Cart cart=new Cart();
    private final ArrayDeque<Scan> scans=new ArrayDeque<>();
    private DefaultTableModel model;
    private PendingStore pendingStore;
    private SaleRequest pending;
    private boolean scanning,busy;
    private final JLabel totalLabel=new JLabel("Tạm tính: 0 ₫");
    private final JButton retry=Ui.button("Thử lại cùng yêu cầu",()->payPending());
    private final JButton resolve=Ui.button("Đối soát yêu cầu chờ",()->resolvePending());
    private record Scan(String code,int quantity){}
    private record ScanResult(Item product,List<String> serials){}

public MakeSaleUI(){
initComponents();

    App.guard(this,Role.MANAGER,Role.CASHIER);
    model=Ui.model();model.setColumnIdentifiers(new Object[]{"SKU","Sản phẩm","SL","Đơn giá","Thành tiền"});jTable_MakeSale.setModel(model);Ui.setupTable(jTable_MakeSale);
    jtf_MSItemSku.setText("");jTextField_CustomerCNIC.setText("");jTextField_CustomerCNIC.setToolTipText("Bỏ trống cho khách lẻ. Mã khách hàng được hiển thị trong màn hình Khách hàng.");
    jS_MSQuantity.setModel(new SpinnerNumberModel(1,1,100000,1));
    jToggleButton3.setIcon(null);jb_MSAdd.setIcon(null);jb_MSRemove.setIcon(null);
    jToggleButton2.setText("Thanh toán");jToggleButton3.setText("Tìm KH");jb_MSAdd.setText("Thêm");jb_MSRemove.setText("Xóa dòng");
    jtf_MSItemSku.setToolTipText("Máy quét USB HID: mỗi Enter thêm 1 chiếc. Nhập tay: chọn số lượng rồi bấm Thêm.");
    JPanel footer=new JPanel(new FlowLayout(FlowLayout.LEFT));footer.add(totalLabel);footer.add(Ui.button("Tìm sản phẩm",()->new ProductsUI().setVisible(true)));footer.add(retry);footer.add(resolve);
    JPanel shell=new JPanel(new BorderLayout());shell.add(jPanel2);shell.add(footer,BorderLayout.SOUTH);setContentPane(shell);
    try{pendingStore=new PendingStore(App.pendingDirectory(),App.session().employeeId(),"sale");pending=pendingStore.sale();}catch(java.io.IOException e){if(pendingStore!=null)try{pendingStore.close();}catch(java.io.IOException close){e.addSuppressed(close);}dispose();throw new IllegalStateException(e.getMessage(),e);}
    setSize(1180,790);Ui.decorate(this);setLocationRelativeTo(null);render();
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);addWindowListener(new java.awt.event.WindowAdapter(){
        public void windowClosing(java.awt.event.WindowEvent e){if(busy||scanning){Ui.message(MakeSaleUI.this,"Đang xử lý yêu cầu. Không đóng cửa sổ trong khi ghi dữ liệu.");return;}if(pending!=null||!cart.lines().isEmpty()){if(!Ui.confirm(MakeSaleUI.this,"Đóng POS? Giỏ chưa thanh toán bị bỏ; yêu cầu chờ sẽ được giữ để đối soát khi mở lại."))return;}dispose();}
        public void windowClosed(java.awt.event.WindowEvent e){try{pendingStore.close();}catch(java.io.IOException ex){Ui.error(null,ex);}}
        public void windowOpened(java.awt.event.WindowEvent e){jtf_MSItemSku.requestFocusInWindow();if(pending!=null)Ui.message(MakeSaleUI.this,"Có yêu cầu chưa đối soát: "+pending.requestId()+". Không tạo giao dịch mới; dùng Đối soát hoặc Thử lại.");}
    });

}

    private void enqueue(int quantity){
        if(busy||pending!=null)return;String code=jtf_MSItemSku.getText().trim();if(code.isEmpty())return;
        jtf_MSItemSku.setText("");jtf_MSItemSku.requestFocusInWindow();
        if(scans.size()>=100){Ui.message(this,"Hàng đợi quét đã đầy. Kiểm tra giỏ hàng trước khi quét tiếp.");return;}
        scans.add(new Scan(code,quantity));nextScan();
    }
    private void nextScan(){
        if(scanning||scans.isEmpty())return;scanning=true;render();Scan scan=scans.removeFirst();
        Ui.db(this,s->{Item item=App.system().getStore().catalog().find(s,scan.code());return new ScanResult(item,item.trackSerial()?App.system().getStore().catalog().serials(s,item.id(),"QUAY"):List.of());},result->{
            try{
                List<String> serials=List.of();
                if(result.product().trackSerial()){
                    JList<String> choices=new JList<>(result.serials().toArray(String[]::new));choices.setVisibleRowCount(8);choices.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
                    if(!Ui.edit(this,"Chọn đúng "+scan.quantity()+" Serial/IMEI thực tế của "+result.product().name(),Ui.fields("Thiết bị tại quầy",new JScrollPane(choices)))){finishScan();return;}
                    serials=choices.getSelectedValuesList();
                }
                cart.add(result.product(),scan.quantity(),serials);
            }catch(RuntimeException ex){Ui.error(this,ex);}finishScan();
        },error->{Ui.error(this,error);finishScan();});
    }
    private void finishScan(){scanning=false;render();jtf_MSItemSku.requestFocusInWindow();nextScan();}
    private void render(){
        if(model==null)return;model.setRowCount(0);List<ItemQuantity> lines=pending==null?cart.lines():pending.lines();
        for(ItemQuantity l:lines)model.addRow(new Object[]{l.sku(),l.name(),l.quantity(),l.expectedPrice(),l.gross()});
        totalLabel.setText(pending==null?"Tạm tính: "+Money.format(cart.subtotal()):"Yêu cầu chờ: "+pending.requestId());
        boolean editable=pending==null&&!busy;
        jtf_MSItemSku.setEnabled(editable);jb_MSAdd.setEnabled(editable);jb_MSRemove.setEnabled(editable&&!scanning);jTextField_CustomerCNIC.setEnabled(editable);jS_MSQuantity.setEnabled(editable);
        jToggleButton2.setEnabled(editable&&!scanning&&scans.isEmpty()&&!cart.lines().isEmpty());retry.setEnabled(pending!=null&&!busy);resolve.setEnabled(pending!=null&&!busy);
    }
    private void remove(){if(pending!=null||busy||scanning)return;int row=jTable_MakeSale.getSelectedRow();if(row<0)throw new IllegalArgumentException("Chọn dòng cần xóa.");cart.remove(cart.lines().get(jTable_MakeSale.convertRowIndexToModel(row)).productId());render();}
    private void checkout(){
        if(busy||scanning||pending!=null||cart.lines().isEmpty())return;
        JTextField manual=Ui.text("0"),code=Ui.text("");JComboBox<String> payment=new JComboBox<>(new String[]{"Tiền mặt","Chuyển khoản"});
        if(!Ui.edit(this,"Thanh toán",Ui.fields("Phương thức",payment,"Giảm trực tiếp (VND)",manual,"Hoặc mã khuyến mãi",code)))return;
        BigDecimal discount=Money.of(manual.getText());String promotion=code.getText().trim(),method=payment.getSelectedIndex()==0?"CASH":"TRANSFER";
        String customerText=jTextField_CustomerCNIC.getText().trim();Long customer=customerText.isBlank()?null:Long.valueOf(customerText);
        List<ItemQuantity> lines=cart.lines();busy=true;render();
        Ui.db(this,s->App.system().getStore().sales().quote(s,lines,promotion,discount),quote->{
            busy=false;
            if(!Ui.confirm(this,"Tạm tính: "+Money.format(quote.subtotal())+"\nGiảm giá: "+Money.format(quote.discount())+"\nPhải thu: "+Money.format(quote.total())+"\nĐã nhận thanh toán và ghi hóa đơn?")){render();return;}
            pending=new SaleRequest(UUID.randomUUID(),customer,method,discount,promotion,quote.discount(),lines);payPending();
        },error->{busy=false;render();Ui.error(this,error);});
    }
    private void payPending(){
        if(pending==null||busy)return;SaleRequest request=pending;busy=true;render();
        Ui.db(this,s->{pendingStore.save(request);Sale sale=App.system().getStore().sales().checkout(s,request);pendingStore.clear();return sale;},this::paid,error->{busy=false;render();Ui.error(this,error);});
    }
    private void paid(Sale sale){busy=false;pending=null;cart.clear();jTextField_CustomerCNIC.setText("");render();Ui.receipt(sale);jtf_MSItemSku.requestFocusInWindow();}
    private void resolvePending(){
        if(pending==null||busy)return;SaleRequest request=pending;busy=true;render();
        Ui.db(this,s->{Sale sale=App.system().getStore().sales().resolve(s,request);if(sale!=null)pendingStore.clear();return sale;},sale->{
            busy=false;if(sale!=null){paid(sale);return;}
            if(Ui.confirm(this,"SQL Server xác nhận yêu cầu chưa được ghi. Hủy yêu cầu chờ để sửa giỏ hàng?")){
                try{pendingStore.clear();pending=null;}catch(java.io.IOException e){Ui.error(this,e);}
            }render();
        },error->{busy=false;render();Ui.error(this,error);});
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel2 = new javax.swing.JPanel();
        jLabel_MakeSaleTitle = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jTextField_CustomerCNIC = new javax.swing.JTextField();
        jToggleButton1 = new javax.swing.JToggleButton();
        jLabel3 = new javax.swing.JLabel();
        jtf_MSItemSku = new javax.swing.JTextField();
        jLabel4 = new javax.swing.JLabel();
        jS_MSQuantity = new javax.swing.JSpinner();
        jLabel5 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        jb_MSAdd = new javax.swing.JToggleButton();
        jb_MSRemove = new javax.swing.JToggleButton();
        jToggleButton2 = new javax.swing.JToggleButton();
        jToggleButton3 = new javax.swing.JToggleButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        jTable_MakeSale = new javax.swing.JTable();
        jLabel7 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("BÁN HÀNG");
        setPreferredSize(new java.awt.Dimension(900, 665));
        setResizable(false);

        jPanel2.setBackground(new java.awt.Color(1, 35, 53));

        jLabel_MakeSaleTitle.setBackground(new java.awt.Color(255, 255, 153));
        jLabel_MakeSaleTitle.setFont(new java.awt.Font("Magneto", 0, 48)); // NOI18N
        jLabel_MakeSaleTitle.setForeground(new java.awt.Color(255, 255, 255));
        jLabel_MakeSaleTitle.setText("BÁN HÀNG");

        jPanel1.setBackground(new java.awt.Color(1, 35, 53));
        jPanel1.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jPanel1MouseClicked(evt);
            }
        });

        jLabel1.setBackground(new java.awt.Color(255, 255, 255));
        jLabel1.setFont(new java.awt.Font("sansserif", 1, 14)); // NOI18N
        jLabel1.setForeground(new java.awt.Color(255, 255, 255));
        jLabel1.setText("Mã KH:");

        jTextField_CustomerCNIC.setText("");
        jTextField_CustomerCNIC.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jTextField_CustomerCNICActionPerformed(evt);
            }
        });

        jToggleButton1.setBackground(new java.awt.Color(114, 114, 244));
        jToggleButton1.setFont(new java.awt.Font("sansserif", 1, 12)); // NOI18N
        jToggleButton1.setForeground(new java.awt.Color(255, 255, 255));
        jToggleButton1.setText("Khách hàng");
        jToggleButton1.setBorderPainted(false);
        jToggleButton1.setOpaque(true);
        jToggleButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jToggleButton1ActionPerformed(evt);
            }
        });

        jLabel3.setFont(new java.awt.Font("sansserif", 1, 14)); // NOI18N
        jLabel3.setForeground(new java.awt.Color(255, 255, 255));
        jLabel3.setText("Mã hàng:");

        jtf_MSItemSku.setText("");
        jtf_MSItemSku.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jtf_MSItemSkuActionPerformed(evt);
            }
        });

        jLabel4.setFont(new java.awt.Font("sansserif", 1, 14)); // NOI18N
        jLabel4.setForeground(new java.awt.Color(255, 255, 255));
        jLabel4.setText("SL:");

        jLabel5.setBackground(new java.awt.Color(255, 255, 255));
        jLabel5.setFont(new java.awt.Font("sansserif", 1, 18)); // NOI18N
        jLabel5.setForeground(new java.awt.Color(255, 255, 255));
        jLabel5.setText("Khách hàng");

        jLabel6.setFont(new java.awt.Font("sansserif", 1, 18)); // NOI18N
        jLabel6.setForeground(new java.awt.Color(255, 255, 255));
        jLabel6.setText("Hàng hóa");

        jb_MSAdd.setBackground(new java.awt.Color(114, 114, 244));
        jb_MSAdd.setFont(new java.awt.Font("sansserif", 1, 12)); // NOI18N
        jb_MSAdd.setForeground(new java.awt.Color(255, 255, 255));
        jb_MSAdd.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/plus.png"))); // NOI18N
        jb_MSAdd.setText("Thêm");
        jb_MSAdd.setBorderPainted(false);
        jb_MSAdd.setOpaque(true);
        jb_MSAdd.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jb_MSAddActionPerformed(evt);
            }
        });

        jb_MSRemove.setBackground(new java.awt.Color(114, 114, 244));
        jb_MSRemove.setFont(new java.awt.Font("sansserif", 1, 12)); // NOI18N
        jb_MSRemove.setForeground(new java.awt.Color(255, 255, 255));
        jb_MSRemove.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/shopping-cart.png"))); // NOI18N
        jb_MSRemove.setText("Xóa dòng");
        jb_MSRemove.setBorderPainted(false);
        jb_MSRemove.setOpaque(true);
        jb_MSRemove.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jb_MSRemoveActionPerformed(evt);
            }
        });

        jToggleButton2.setBackground(new java.awt.Color(114, 114, 244));
        jToggleButton2.setFont(new java.awt.Font("sansserif", 1, 12)); // NOI18N
        jToggleButton2.setForeground(new java.awt.Color(255, 255, 255));
        jToggleButton2.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/receipt.png"))); // NOI18N
        jToggleButton3.setIcon(null);jb_MSAdd.setIcon(null);jb_MSRemove.setIcon(null);
    jToggleButton2.setText("Thanh toán");
        jToggleButton2.setBorderPainted(false);
        jToggleButton2.setOpaque(true);
        jToggleButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jToggleButton2ActionPerformed(evt);
            }
        });

        jToggleButton3.setBackground(new java.awt.Color(114, 114, 244));
        jToggleButton3.setFont(new java.awt.Font("sansserif", 1, 12)); // NOI18N
        jToggleButton3.setForeground(new java.awt.Color(255, 255, 255));
        jToggleButton3.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/check-mark.png"))); // NOI18N
        jToggleButton3.setText("Tìm KH");
        jToggleButton3.setBorderPainted(false);
        jToggleButton3.setOpaque(true);
        jToggleButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jToggleButton3ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(14, 14, 14)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel5, javax.swing.GroupLayout.PREFERRED_SIZE, 288, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel6, javax.swing.GroupLayout.PREFERRED_SIZE, 179, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(176, 176, 176)
                        .addComponent(jToggleButton2))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(11, 11, 11)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(jToggleButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 184, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addGroup(jPanel1Layout.createSequentialGroup()
                                        .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 93, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(jTextField_CustomerCNIC, javax.swing.GroupLayout.PREFERRED_SIZE, 184, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                .addGap(18, 18, 18)
                                .addComponent(jToggleButton3, javax.swing.GroupLayout.PREFERRED_SIZE, 111, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, 75, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(jPanel1Layout.createSequentialGroup()
                                        .addComponent(jtf_MSItemSku, javax.swing.GroupLayout.PREFERRED_SIZE, 175, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addGap(28, 28, 28)
                                        .addComponent(jLabel4, javax.swing.GroupLayout.PREFERRED_SIZE, 67, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addGap(18, 18, 18)
                                        .addComponent(jS_MSQuantity, javax.swing.GroupLayout.PREFERRED_SIZE, 86, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(jPanel1Layout.createSequentialGroup()
                                        .addComponent(jb_MSAdd, javax.swing.GroupLayout.PREFERRED_SIZE, 111, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addGap(47, 47, 47)
                                        .addComponent(jb_MSRemove, javax.swing.GroupLayout.PREFERRED_SIZE, 137, javax.swing.GroupLayout.PREFERRED_SIZE)))))))
                .addContainerGap(101, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(43, 43, 43)
                .addComponent(jLabel5, javax.swing.GroupLayout.PREFERRED_SIZE, 42, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jTextField_CustomerCNIC, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jToggleButton3)
                    .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addComponent(jToggleButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 41, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 55, Short.MAX_VALUE)
                .addComponent(jLabel6, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jtf_MSItemSku, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, 24, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jS_MSQuantity, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel4))
                .addGap(32, 32, 32)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jb_MSAdd)
                    .addComponent(jb_MSRemove))
                .addGap(18, 18, 18)
                .addComponent(jToggleButton2, javax.swing.GroupLayout.PREFERRED_SIZE, 49, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        jTable_MakeSale.setBackground(new java.awt.Color(153, 153, 255));
        jTable_MakeSale.setBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.RAISED, java.awt.Color.orange, java.awt.Color.red, java.awt.Color.orange, java.awt.Color.pink));
        jTable_MakeSale.setForeground(new java.awt.Color(255, 255, 255));
        jTable_MakeSale.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Item SKU", "Quantity", "Price"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jTable_MakeSale.setGridColor(new java.awt.Color(0, 0, 0));
        jTable_MakeSale.setShowGrid(true);
        jScrollPane2.setViewportView(jTable_MakeSale);

        jLabel7.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/shopping-bag.png"))); // NOI18N
        jLabel7.setText("");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(49, 49, 49)
                .addComponent(jLabel_MakeSaleTitle, javax.swing.GroupLayout.PREFERRED_SIZE, 449, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel7, javax.swing.GroupLayout.PREFERRED_SIZE, 67, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 610, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(16, 16, 16))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGap(152, 152, 152)
                        .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 430, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel2Layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabel_MakeSaleTitle, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel7, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(129, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );

        pack();
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents
    private void jb_MSAddActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jb_MSAddActionPerformed
        Ui.safe(()->enqueue((Integer)jS_MSQuantity.getValue()));
    }//GEN-LAST:event_jb_MSAddActionPerformed
    private void jtf_MSItemSkuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jtf_MSItemSkuActionPerformed
        Ui.safe(()->enqueue(1));
    }//GEN-LAST:event_jtf_MSItemSkuActionPerformed
    private void jPanel1MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jPanel1MouseClicked
        // No business action for this design-time event.
    }//GEN-LAST:event_jPanel1MouseClicked
    private void jb_MSRemoveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jb_MSRemoveActionPerformed
        Ui.safe(this::remove);
    }//GEN-LAST:event_jb_MSRemoveActionPerformed
    private void jTextField_CustomerCNICActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextField_CustomerCNICActionPerformed
        // No business action for this design-time event.
    }//GEN-LAST:event_jTextField_CustomerCNICActionPerformed
    private void jToggleButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jToggleButton3ActionPerformed
        Ui.safe(()->{
            String text=jTextField_CustomerCNIC.getText().trim();
            if(text.isEmpty()){Ui.message(this,"Để trống mã khách hàng để bán cho khách lẻ.");return;}
            long id=Long.parseLong(text);
            Ui.db(this,s->App.system().people.customer(s,id),c->Ui.message(this,c.name()+" — "+c.phone()));
        });
    }//GEN-LAST:event_jToggleButton3ActionPerformed
    private void jToggleButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jToggleButton2ActionPerformed
        Ui.safe(this::checkout);
    }//GEN-LAST:event_jToggleButton2ActionPerformed
    private void jToggleButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jToggleButton1ActionPerformed
        Ui.safe(PeopleUI::customers);
    }//GEN-LAST:event_jToggleButton1ActionPerformed
    public static void main(String[] args){App.main(args);}
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel_MakeSaleTitle;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JSpinner jS_MSQuantity;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTable jTable_MakeSale;
    private javax.swing.JTextField jTextField_CustomerCNIC;
    private javax.swing.JToggleButton jToggleButton1;
    private javax.swing.JToggleButton jToggleButton2;
    private javax.swing.JToggleButton jToggleButton3;
    private javax.swing.JToggleButton jb_MSAdd;
    private javax.swing.JToggleButton jb_MSRemove;
    private javax.swing.JTextField jtf_MSItemSku;
    // End of variables declaration
}
