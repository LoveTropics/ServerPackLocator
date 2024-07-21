package cpw.mods.forge.serverpacklocator;

import cpw.mods.modlauncher.Launcher;

public class LaunchProgressReporter {
    public static void add(String message) {
        final Launcher launcher = Launcher.INSTANCE;
        if (launcher != null) {
            launcher.environment().getProperty(net.neoforged.neoforgespi.Environment.Keys.PROGRESSMESSAGE.get())
                    .ifPresent(consumer -> consumer.accept(message));
        }
    }
}
