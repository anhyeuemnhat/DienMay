package tests;

import Code.*;
import GUI.*;
import Service.AuthService;
import javax.swing.*;
import java.awt.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import static tests.TestKit.*;

/** VISUAL/WIRING ONLY: synthetic sessions are not registered in AuthService.
 * No business DAO is invoked. Robot captures the real virtual display after the EDT is idle. */
public final class UiSmoke {
    private static Path out;
    private static final Robot robot=createRobot();
    private static Robot createRobot(){try{return GraphicsEnvironment.isHeadless()?null:new Robot();}catch(AWTException e){throw new IllegalStateException(e);}}
    private static <T> T edt(Callable<T> action)throws Exception {
        FutureTask<T> task=new FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }
    private static void session(Role role)throws Exception {
        Constructor<AuthService.Session> c=AuthService.Session.class.getDeclaredConstructor(long.class,String.class,Role.class,int.class,boolean.class);
        c.setAccessible(true);
        Field f=App.class.getDeclaredField("session");f.setAccessible(true);
        f.set(null,c.newInstance(99999,"NHÂN VIÊN KIỂM THỬ UI",role,1,false));
    }
    private static Object field(Object object,String name)throws Exception {
        Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
    }
    private static void capture(String name,JFrame frame)throws Exception {
        edt(()->{frame.setVisible(true);frame.validate();return null;});
        robot.waitForIdle();Thread.sleep(200);
        Rectangle bounds=edt(()->{
            check(frame.isShowing(),"Window not showing");
            JMenuBar menu=frame.getJMenuBar();
            if(menu!=null && menu.getMenuCount()>0){
                check(menu.isShowing() && menu.getHeight()>0,"Menu bar clipped");
                Point menuOrigin=SwingUtilities.convertPoint(menu,0,0,frame.getContentPane());
                check(menuOrigin.y<0,"Menu overlaps the content pane");
            }
            return frame.getBounds();
        });
        ImageIO.write(robot.createScreenCapture(bounds),"png",out.resolve(name+".png").toFile());
        edt(()->{frame.dispose();return null;});
    }
    public static void main(String[] args)throws Exception {
        if(robot==null){System.out.println("NOT RUN | UI smoke needs a real display or Xvfb.");return;}
        System.setProperty("dienmay.stateDir",Files.createTempDirectory("dm-ui-state").toString());
        out=Path.of("build/ui-smoke");Files.createDirectories(out);App.system();
        try {
            MainUI login=edt(()->{
                MainUI f=new MainUI();
                check(!((JComboBox<?>)field(f,"jComboBox1")).isVisible(),"Role must not be user-selectable");
                equal(0,((JPasswordField)field(f,"jPasswordField_LogInPass")).getPassword().length);return f;
            });
            capture("01-login",login);
            System.out.println("PASS | UI login renders; no role picker or prefilled password");
            capture("02-manager",edt(()->{session(Role.MANAGER);return new StoreManagerMain();}));
            System.out.println("PASS | Original manager form and menu render");
            capture("03-cashier",edt(()->{session(Role.CASHIER);return new CashierMain();}));
            MakeSaleUI pos=edt(()->{
                MakeSaleUI f=new MakeSaleUI();
                equal(1,((JTextField)field(f,"jtf_MSItemSku")).getActionListeners().length);
                check(!((JTable)field(f,"jTable_MakeSale")).getModel().isCellEditable(0,0),"POS table editable");return f;
            });
            capture("04-pos",pos);
            System.out.println("PASS | Original POS renders; one Enter listener and read-only sale table");
            capture("05-warehouse",edt(()->{session(Role.WAREHOUSE);return new WarehouseManagerMain();}));
            capture("06-transfer",edt(TransferfromWarehouse::new));
            System.out.println("PASS | Original warehouse and transfer forms render");
        }finally{
            edt(()->{for(Window w:Window.getWindows())w.dispose();return null;});
        }
        System.out.println("RESULT | UI_SMOKE=PASS | 6 Robot screenshots; synthetic display-only sessions, no SQL Server/peripheral validation.");
        System.exit(0);
    }
}
