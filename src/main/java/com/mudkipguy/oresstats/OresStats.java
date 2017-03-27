package com.mudkipguy.oresstats;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent.KeyInputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;

@Mod(modid = OresStats.MODID, version = OresStats.VERSION)
public class OresStats
{
    public static final String MODID = "oresstats";
    public static final String VERSION = "1.0";
    public static final String myName = "OresStats";
    
    static FileWriter outFile;
    static Minecraft mc = Minecraft.getMinecraft();
    static File myDir;
    static boolean doLogging = true;
    static KeyBinding toggleLogging = new KeyBinding("Toggle", Keyboard.KEY_O, myName);
    static String lastBreakStats = "";
    
    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        ClientRegistry.registerKeyBinding(toggleLogging);
        myDir = new File(mc.mcDataDir, "/" + myName + "/");
        if(!myDir.isDirectory()) myDir.mkdir();
        cleanUp();
    }
    
    //zip old logs and remove the empty ones
    public static void cleanUp(){
        for(File f : myDir.listFiles()){
            if(f.isFile() && f.getName().endsWith(".txt")){
                try{
                    if(f.length() > 0){
                        String path = f.getAbsolutePath().substring(0, f.getAbsolutePath().length() - 4) + ".zip";
                        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(path));
                        out.putNextEntry(new ZipEntry(f.getName()));
                        BufferedReader fr = new BufferedReader(new FileReader(f));
                        String line = "";
                        while((line=fr.readLine()) != null){
                            line += "\r\n"; //this feels awkward but it works
                            out.write(line.getBytes(), 0, line.getBytes().length);
                        }
                        fr.close();
                        out.closeEntry();
                        out.close();
                    }
                    f.delete();
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        }
    }
    
    //Looks for HiddenOre drops & logs them
    @SubscribeEvent
    public void onChatRecieved(ClientChatReceivedEvent message) {
        String magicWords = "You found a hidden ore!"; //Assume that the default phrase is used
        if(message.getMessage().getUnformattedText().startsWith(magicWords)){
            String messageText = message.getMessage().getUnformattedText().replace('\"', '\'');
            log("{\"type\":\"drop\",\"message\":\"" + messageText + "\"," + lastBreakStats + ",\"hash\":" + messageText.hashCode() + "}\r\n");
        }
    }
    
    //Looks for stone/cobble from broken stone & logs it
    @SubscribeEvent
    public void onTick(ClientTickEvent event) {
        if(event.phase == TickEvent.Phase.START && mc.theWorld != null) {
            for(Object o : mc.theWorld.loadedEntityList) {
                if(o instanceof EntityItem) {
                    EntityItem e = (EntityItem) o;
                    double distance = Math.sqrt(Math.pow(e.posX - mc.thePlayer.posX, 2) + Math.pow(e.posY - mc.thePlayer.posY, 2) + Math.pow(e.posZ - mc.thePlayer.posZ, 2));
                    if(distance < 20 && cameFromBlockBreak(e)
                        && (e.getEntityItem().getItem().equals(Item.getByNameOrId("minecraft:cobblestone"))
                            || e.getEntityItem().getItem().equals(Item.getByNameOrId("minecraft:stone")))) {
                        //if we're in here then the item "very likely" came from a block break
                        int hiImPosY = (int)e.posY;
                        int hash = new HashCodeBuilder(17, 101).append(hash(e)).append(currentToolJson().hashCode())
                                .append(hiImPosY).toHashCode();
                        lastBreakStats = "\"tool\":" + currentToolJson() + ",\"height\":" + hiImPosY
                                + ",\"biome\":\"" + getBiome(e) + "\",\"dimension\":" + e.dimension;
                        log("{\"type\":\"break\"," + lastBreakStats + ",\"hash\":" + hash + "}\r\n");
                    }
                }
            }
        }
    }
    
    @SubscribeEvent
    public void keyPress(KeyInputEvent event) {
        if(toggleLogging.isKeyDown()) {
            doLogging ^= true;
            say(doLogging ? "Logging enabled" : "Logging disabled");
        }
    }
    
    public static void say(String text){
        mc.thePlayer.addChatMessage(
                new TextComponentString(
                        TextFormatting.GREEN + "[" + myName + "] " + 
                        TextFormatting.GRAY + text)
            );
    }
    
    public static void log(String text){
        if(doLogging){
            String errorMessage = "Couldn't log ore stats. To disable logging press " + toggleLogging.getDisplayName();
            if(outFile == null){
                DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
                int counter=0;
                File logFile;
                Date now = new Date();
                do{//this will probably never go more than once
                    logFile = new File(myDir, dateFormat.format(now) + " " + counter++ + ".txt");
                }while(logFile.exists()); 
                try {
                    logFile.createNewFile();
                    outFile = new FileWriter(logFile);
                } catch (Exception e) {} //we'll tell them at the next catch block
            }
            try{
                outFile.write(text);
                outFile.flush(); //this should probably be fine
            }catch(Exception e){
                e.printStackTrace();
                say(errorMessage);
            }
        }
    }
    
    //@TODO: improve accuracy by using x,z velocity to extrapolate the item's point of origin
    private static boolean cameFromBlockBreak(EntityItem e){
        return (e.motionY == 0.15675 || e.motionY == 0) && e.ticksExisted == 0;
    }
    
    public static String currentToolJson(){
        ItemStack tool = mc.thePlayer.inventory.getCurrentItem();
        if(tool == null) return "{}";
        String toolName = tool.getUnlocalizedName();
        return "{\"item\":\"" + toolName + "\"" + (tool.getEnchantmentTagList() == null ? "" : ",\"enchants\":\"" + tool.getEnchantmentTagList().toString() + "\"") + "}";
    }
    
    public static String getBiome(EntityItem e){
        BlockPos breakLocation = new BlockPos(e.posX, e.posY, e.posZ);
        Chunk chunk = mc.theWorld.getChunkFromBlockCoords(breakLocation);
        if (!chunk.isEmpty()){
            return chunk.getBiome(breakLocation, mc.theWorld.getBiomeProvider()).getBiomeName();
        }else{
            return "Error";
        }
    }
    
    public static int hash(EntityItem e){
        int multiplier = 8001;
        HashCodeBuilder b = new HashCodeBuilder(17, 51);
        b.append(e.dimension * multiplier);
        b.append((int)(e.motionX * multiplier));
        b.append((int)(e.motionY * multiplier));
        b.append((int)(e.motionZ * multiplier));
        return b.toHashCode();
    }
}
