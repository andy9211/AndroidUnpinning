public class Main   implements IXposedHookLoadPackage , IXposedHookZygoteInit {


    @Override
    public void initZygote(IXposedHookZygoteInit.StartupParam startupParam) throws Throwable {
        initZygote(startupParam);
    }
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam loadPackageParam){
        Unpinning.doing(loadPackageParam);
    }
}
