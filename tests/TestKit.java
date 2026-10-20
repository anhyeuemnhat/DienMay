package tests;
import java.util.Objects;
public final class TestKit {
    @FunctionalInterface public interface Checked {void run()throws Exception;}
    private int passed,failed;
    public void test(String name,Checked test){try{test.run();passed++;System.out.println("PASS | "+name);}catch(Throwable e){failed++;System.out.println("FAIL | "+name+" | "+e);e.printStackTrace(System.out);}}
    public static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void equal(Object expected,Object actual){if(!Objects.equals(expected,actual))throw new AssertionError("expected="+expected+", actual="+actual);}
    public static <T extends Throwable> T throwsType(Class<T> type,Checked action){try{action.run();}catch(Throwable e){if(type.isInstance(e))return type.cast(e);throw new AssertionError("Expected "+type.getName()+", got "+e,e);}throw new AssertionError("Expected "+type.getName()+", but call succeeded");}
    public void finish(){System.out.println("RESULT | PASS="+passed+" FAIL="+failed);if(failed!=0)throw new AssertionError(failed+" test(s) failed");}
}
