package yt.mak.fire_items;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import yt.mak.fire_items.item.ModItems;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Mod(Fire_Items.MOD_ID)
public class Fire_Items {

    public static final String MOD_ID = "fire_items";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static Story story;

    public Fire_Items() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.register(modEventBus);
        MinecraftForge.EVENT_BUS.register(this);
        loadStory();
    }

    private void loadStory() {
        try (InputStreamReader reader = new InputStreamReader(
                getClass().getResourceAsStream("/assets/fire_items/story/story.json"), StandardCharsets.UTF_8)) {

            Type storyType = new TypeToken<Story>(){}.getType();
            story = GSON.fromJson(reader, storyType);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Story getStory() {
        return story;
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (story != null) {
            triggerEvent("player_join", event.getEntity());
        }
    }

    @SubscribeEvent
    public void onPlayerDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        triggerEvent("player_dimension_change", event.getEntity());
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (story == null) return;

        Player player = event.player;
        BlockPos playerPos = player.blockPosition();

        for (Story.Event storyEvent : story.events) {
            if (storyEvent.trigger.equals("player_reach_coordinates") && !storyEvent.hasTriggered) {
                BlockPos targetPos = new BlockPos(
                        storyEvent.coordinates.x,
                        storyEvent.coordinates.y,
                        storyEvent.coordinates.z
                );

                // Добавление области допуска
                if (playerPos.closerThan(targetPos, 2)) {
                    storyEvent.execute(player);
                    storyEvent.hasTriggered = true;
                }
            }
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Можно использовать для инициализации сервера
    }

    private void triggerEvent(String trigger, Player player) {
        if (story != null) {
            for (Story.Event event : story.events) {
                if (event.trigger.equals(trigger) && !event.hasTriggered) {
                    event.execute(player);
                    event.hasTriggered = true;  // Устанавливаем флаг, что событие было выполнено
                }
            }
        }
    }

    public static class Story {
        List<Event> events;
        private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        public static class Event {
            String id;
            String trigger;
            Coordinates coordinates;
            List<Action> actions;
            boolean hasTriggered = false;  // Новый флаг, чтобы отслеживать, выполнено ли событие

            void execute(Player player) {
                int delay = 5; // Начальная задержка
                for (Action action : actions) {
                    delay += 3; // Добавляем 3 секунды к задержке
                    scheduler.schedule(() -> action.execute(player), delay, TimeUnit.SECONDS);
                }
            }
        }

        public static class Coordinates {
            int x;
            int y;
            int z;
        }

        public static class Action {
            String type;
            String text;
            String item;
            int count;
            String npc_id;
            Coordinates location;

            void execute(Player player) {
                if (type.equals("message")) {
                    if (player instanceof ServerPlayer serverPlayer) {
                        serverPlayer.sendSystemMessage(Component.literal("БОГ: " + text));
                    }
                } else if (type.equals("give_item")) {
                    ItemStack itemStack = new ItemStack(Registry.ITEM.get(new ResourceLocation(item)), count);
                    player.addItem(itemStack);
                } else if (type.equals("spawn_npc")) {
                    if (player.level instanceof ServerLevel serverLevel) {
                        EntityType<?> entityType = Registry.ENTITY_TYPE.get(new ResourceLocation(npc_id));
                        Entity entity = entityType.create(serverLevel);
                        if (entity != null) {
                            entity.moveTo(location.x, location.y, location.z);
                            serverLevel.addFreshEntity(entity);

                            // Делаем NPC неподвижным
                            if (entity instanceof Mob mob) {
                                mob.goalSelector.removeAllGoals(); // Удаляем все текущие цели

                                // Добавляем пустую цель, чтобы NPC не двигался
                                mob.goalSelector.addGoal(0, new Goal() {
                                    @Override
                                    public boolean canUse() {
                                        return true; // Цель всегда активна
                                    }

                                    @Override
                                    public boolean requiresUpdateEveryTick() {
                                        return true; // Обновляется каждый тик
                                    }

                                    @Override
                                    public void tick() {
                                        mob.getNavigation().stop(); // Останавливаем навигацию
                                        mob.setPos(location.x, location.y, location.z); // Удерживаем NPC на месте
                                    }
                                });
                            }
                        }
                    }
                }
            }
        }

        private void commonSetup(final FMLCommonSetupEvent event) {

        }

        @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
        public static class ClientModEvents {
            @SubscribeEvent
            public static void onClientSetup(FMLClientSetupEvent event) {
            }
        }
    }
}