/*
 * BluSunrize
 * Copyright (c) 2017
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.client.models.connection;

import blusunrize.immersiveengineering.ImmersiveEngineering;
import blusunrize.immersiveengineering.client.models.ModelData;
import blusunrize.immersiveengineering.client.models.multilayer.MultiLayerModel;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.IUnbakedModel;
import net.minecraft.client.renderer.model.ModelBakery;
import net.minecraft.client.renderer.texture.ISprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ICustomModelLoader;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;

public class ConnectionLoader implements ICustomModelLoader
{
	public static final ResourceLocation DATA_BASED_LOC = new ResourceLocation(ImmersiveEngineering.MODID, "models/connector");
	public static final ImmutableSet<BlockRenderLayer> ALL_LAYERS = ImmutableSet.copyOf(BlockRenderLayer.values());

	@Override
	public void onResourceManagerReload(@Nonnull IResourceManager resourceManager)
	{
		BakedConnectionModel.cache.invalidateAll();
	}

	@Override
	public boolean accepts(@Nonnull ResourceLocation modelLocation)
	{
		return modelLocation.equals(DATA_BASED_LOC);
	}

	@Nonnull
	@Override
	public IUnbakedModel loadModel(@Nonnull ResourceLocation modelLocation)
	{
		return new ConnectorModel();
	}

	public static class ConnectorModel implements IUnbakedModel
	{
		private static final ResourceLocation WIRE_LOC = new ResourceLocation(ImmersiveEngineering.MODID.toLowerCase(Locale.ENGLISH)+":block/wire");
		@Nullable
		private final ModelData baseData;
		@Nonnull
		private final ImmutableSet<BlockRenderLayer> layers;
		@Nonnull
		private final ImmutableMap<String, String> externalTextures;

		public ConnectorModel()
		{
			baseData = null;
			layers = ALL_LAYERS;
			externalTextures = ImmutableMap.of();
		}

		public ConnectorModel(@Nullable ModelData newData, @Nonnull ImmutableSet<BlockRenderLayer> layers,
							  @Nonnull ImmutableMap<String, String> externalTextures)
		{
			this.baseData = newData;
			this.layers = layers;
			this.externalTextures = externalTextures;
		}

		@Nonnull
		@Override
		public Collection<ResourceLocation> getDependencies()
		{
			if(baseData==null)
				return ImmutableList.of();
			baseData.attemptToLoad(false);
			if(baseData.getModel()!=null)
			{
				List<ResourceLocation> ret = new ArrayList<>(baseData.getModel().getDependencies());
				ret.add(0, baseData.location);
				return ret;
			}
			else
				return ImmutableList.of(baseData.location);
		}

		@Nonnull
		@Override
		public Collection<ResourceLocation> getTextures(@Nonnull Function<ResourceLocation, IUnbakedModel> modelGetter,
														@Nonnull Set<String> missingTextureErrors)
		{
			if(baseData==null)
				return ImmutableList.of();
			baseData.attemptToLoad(false);
			if(baseData.getModel()!=null)
			{
				List<ResourceLocation> ret = new ArrayList<>(baseData.getModel().getTextures(modelGetter, missingTextureErrors));
				ret.add(WIRE_LOC);
				return ret;
			}
			else
				return ImmutableList.of(WIRE_LOC);
		}

		@Nullable
		@Override
		public IBakedModel bake(@Nonnull ModelBakery bakery, @Nonnull Function<ResourceLocation, TextureAtlasSprite> spriteGetter,
								@Nonnull ISprite sprite, @Nonnull VertexFormat format)
		{
			Preconditions.checkNotNull(baseData);
			baseData.attemptToLoad(true);
			Preconditions.checkNotNull(baseData.getModel());
			return new BakedConnectionModel(baseData.getModel().bake(bakery, spriteGetter, sprite, format), layers);
		}

		private static final ImmutableSet<String> ownKeys = ImmutableSet.of("base", "custom", "textures", "layers");

		@Nonnull
		@Override
		public IUnbakedModel process(ImmutableMap<String, String> customData)
		{
			if(customData==null||customData.isEmpty()||!customData.containsKey("base"))
				return this;
			JsonObject obj = ModelData.asJsonObject(customData);
			ModelData newData = ModelData.fromJson(obj, ownKeys, "base", externalTextures);
			Collection<BlockRenderLayer> layers = ALL_LAYERS;
			if(obj.has("layers")&&obj.get("layers").isJsonArray())
			{
				JsonArray arr = obj.get("layers").getAsJsonArray();
				layers = new ArrayList<>(arr.size());
				for(JsonElement ele : arr)
				{
					if(ele.isJsonPrimitive()&&ele.getAsJsonPrimitive().isString())
					{
						String layerAsStr = ele.getAsString();
						if(MultiLayerModel.LAYERS_BY_NAME.containsKey(layerAsStr))
							layers.add(MultiLayerModel.LAYERS_BY_NAME.get(layerAsStr));
						else
							throw new RuntimeException("Invalid layer \""+layerAsStr+"\" in wire model");
					}
					else
						throw new RuntimeException("Layers in wire models must be strings! Invalid value: "+ele.toString());
				}
			}
			layers = ImmutableSet.copyOf(layers);
			if(!newData.equals(baseData)||!layers.equals(this.layers))
				return new ConnectorModel(newData, (ImmutableSet<BlockRenderLayer>)layers, externalTextures);
			return this;
		}

		@Nonnull
		@Override
		public IUnbakedModel retexture(ImmutableMap<String, String> textures)
		{
			if(baseData!=null)
			{
				if(!textures.equals(baseData.textures)&&!(textures.isEmpty()&&!baseData.textures.isEmpty()))
					return new ConnectorModel(new ModelData(baseData.location, baseData.data, textures), layers, textures);
			}
			else if(!externalTextures.equals(textures))
				return new ConnectorModel(null, layers, textures);
			return this;
		}
	}
}
