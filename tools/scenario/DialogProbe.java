import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Isolated server-API fixture. It builds a real Paper dialog but has no connected player/client. */
public final class DialogProbe extends JavaPlugin {
    @Override public void onEnable() {
        try {
            JavaPlugin express=(JavaPlugin)getServer().getPluginManager().getPlugin("EnthusiaExpress");
            ClassLoader loader=express.getClass().getClassLoader();
            Class<?> unit=Class.forName("kotlin.Unit",true,loader);
            Class<?> function=Class.forName("kotlin.jvm.functions.Function1",true,loader);
            Object action=Proxy.newProxyInstance(loader,new Class<?>[]{function},(proxy,method,args)->unit.getField("INSTANCE").get(null));
            Class<?> field=Class.forName("io.enthusia.express.infrastructure.gui.MenuField",true,loader);
            Class<?> choice=Class.forName("io.enthusia.express.infrastructure.gui.MenuChoice",true,loader);
            Object input=field.getConstructor(String.class,String.class,String.class,int.class).newInstance("title","Artwork title","Sample",80);
            Object button=choice.getConstructor(String.class,function).newInstance("Continue",action);
            int[] shown={0};
            Player fake=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->{
                switch(method.getName()) {
                    case "getUniqueId": return UUID.fromString("00000000-0000-0000-0000-000000000099");
                    case "getProtocolVersion": return 776;
                    case "isOnline": return true;
                    case "showDialog": shown[0]++; return null;
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy==args[0];
                    case "toString": return "Isolated dialog target";
                    default: return null;
                }
            });
            Class<?> menus=Class.forName("io.enthusia.express.infrastructure.gui.DialogMenus",true,loader);
            Object renderer=menus.getConstructor(JavaPlugin.class).newInstance(express);
            Object result=menus.getMethod("show",Player.class,String.class,String.class,List.class,List.class)
                .invoke(renderer,fake,"Museum API probe","No physical inventory or live client.",List.of(input),List.of(button));
            if(!Boolean.TRUE.equals(result) || shown[0]!=1) throw new IllegalStateException("Dialog builder did not reach the isolated target");
            getLogger().info("EXPRESS_DIALOG_PROBE_PASS: actual Paper builders and callback options resolved; no client rendering claimed");
        } catch(Throwable error) {
            getLogger().severe("EXPRESS_DIALOG_PROBE_FAIL: "+error);
            error.printStackTrace();
        }
    }
}
