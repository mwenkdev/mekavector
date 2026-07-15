package dev.mwenk.mekavector;

import dev.mwenk.mekavector.client.ClientSetup;
import dev.mwenk.mekavector.net.Network;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MekaVector.MOD_ID)
public class MekaVector {

    public static final String MOD_ID = "mekavector";
    public static final Logger LOGGER = LoggerFactory.getLogger("MekaVector");

    public MekaVector() {
        FMLJavaModLoadingContext context = FMLJavaModLoadingContext.get();

        // COMMON config: forward/strafe boost, vector thrust strength, keybind mode.
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        context.getModEventBus().addListener(this::commonSetup);

        // Client-only wiring (keybind + tick listener) lives behind a dist check
        // so the class is never loaded on a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSetup.init(context.getModEventBus());
        }

        MinecraftForge.EVENT_BUS.register(new ServerEvents());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Network::register);
        LOGGER.info("MekaVector loaded — 1.21-style jetpack flight active.");
    }
}
