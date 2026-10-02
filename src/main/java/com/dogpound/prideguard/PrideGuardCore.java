package com.dogpound.prideguard;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import zone.rong.mixinbooter.IEarlyMixinLoader;

/** Loads PrideGuard's packet taps (mixins into the vanilla server network handler) early. */
@IFMLLoadingPlugin.Name("PrideGuard")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1003)
public class PrideGuardCore implements IFMLLoadingPlugin, IEarlyMixinLoader {
    @Override public List<String> getMixinConfigs() { return Collections.singletonList("prideguard.mixins.json"); }
    @Override public void injectData(Map<String, Object> data) {}
    @Override public String[] getASMTransformerClass() { return new String[0]; }
    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public String getAccessTransformerClass() { return null; }
}
