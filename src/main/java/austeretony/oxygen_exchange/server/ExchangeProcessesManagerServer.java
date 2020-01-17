package austeretony.oxygen_exchange.server;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import austeretony.oxygen_core.common.api.CommonReference;
import austeretony.oxygen_core.common.main.EnumOxygenStatusMessage;
import austeretony.oxygen_core.common.main.OxygenMain;
import austeretony.oxygen_core.server.api.CurrencyHelperServer;
import austeretony.oxygen_core.server.api.OxygenHelperServer;
import austeretony.oxygen_core.server.api.PrivilegesProviderServer;
import austeretony.oxygen_exchange.common.EnumExchangeOperation;
import austeretony.oxygen_exchange.common.main.EnumExchangePrivilege;
import austeretony.oxygen_exchange.common.main.ExchangeGUIHandler;
import austeretony.oxygen_exchange.common.main.ExchangeMain;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

public class ExchangeProcessesManagerServer {

    private final Map<Long, ExchangeProcess> exchangeProcesses = new ConcurrentHashMap<>();

    private final Map<Integer, Long> access = new ConcurrentHashMap<>();

    public boolean haveExchangeProcess(int index) {
        return this.access.containsKey(index);   
    }

    public ExchangeProcess getExchangeProcess(int index) {
        return this.exchangeProcesses.get(this.access.get(index));
    }

    public void onPlayerUnloaded(EntityPlayerMP playerMP) {
        this.processExchangeOperation(playerMP, EnumExchangeOperation.CLOSE, 0L);
    }

    public void process() {
        OxygenHelperServer.addRoutineTask(()->{
            Iterator<ExchangeProcess> iterator = this.exchangeProcesses.values().iterator();
            ExchangeProcess exchangeProcess;
            while (iterator.hasNext()) {
                exchangeProcess = iterator.next();
                if (exchangeProcess.process()) {
                    this.access.remove(exchangeProcess.firstParticipant.playerIndex);
                    this.access.remove(exchangeProcess.secondParticipant.playerIndex);
                    if (OxygenHelperServer.isPlayerOnline(exchangeProcess.firstParticipant.playerIndex))
                        exchangeProcess.firstParticipant.player.closeScreen();
                    if (OxygenHelperServer.isPlayerOnline(exchangeProcess.secondParticipant.playerIndex))
                        exchangeProcess.secondParticipant.player.closeScreen();
                    iterator.remove();
                }
            }
        });
    }

    public void sendExchangeRequest(EntityPlayerMP senderMP, int playerIndex) {
        UUID 
        senderUUID = CommonReference.getPersistentUUID(senderMP),
        targetUUID;
        if (OxygenHelperServer.isPlayerOnline(playerIndex)) {
            targetUUID = OxygenHelperServer.getPlayerSharedData(playerIndex).getPlayerUUID();
            EntityPlayerMP target = CommonReference.playerByUUID(targetUUID);
            if (CommonReference.isEntitiesNear(senderMP, target, 5.0D)
                    && !this.haveExchangeProcess(playerIndex)
                    && PrivilegesProviderServer.getAsBoolean(senderUUID, EnumExchangePrivilege.ALLOW_EXCHANGE.id(), true)
                    && PrivilegesProviderServer.getAsBoolean(targetUUID, EnumExchangePrivilege.ALLOW_EXCHANGE.id(), true)
                    && !senderUUID.equals(targetUUID)) {
                OxygenHelperServer.sendRequest(senderMP, target, new ExchangeRequest(ExchangeMain.EXCHANGE_REQUEST_ID, senderUUID, CommonReference.getName(senderMP)));
            } else
                OxygenHelperServer.sendStatusMessage(senderMP, OxygenMain.OXYGEN_CORE_MOD_INDEX, EnumOxygenStatusMessage.REQUEST_RESET.ordinal());
        }
    }

    public void processExchangeRequestReply(EntityPlayer target, UUID senderUUID) {
        if (OxygenHelperServer.isPlayerOnline(senderUUID)) {
            UUID targetUUID = CommonReference.getPersistentUUID(target);
            EntityPlayerMP sender = CommonReference.playerByUUID(senderUUID);
            if (CommonReference.isEntitiesNear(sender, target, 5.0D)) {
                long id = System.nanoTime();
                int 
                firstIndex = OxygenHelperServer.getPlayerIndex(senderUUID),
                secondIndex = OxygenHelperServer.getPlayerIndex(targetUUID);
                this.exchangeProcesses.put(id, new ExchangeProcess(sender, firstIndex, (EntityPlayerMP) target, secondIndex));
                this.access.put(firstIndex, id);
                this.access.put(secondIndex, id);
                openExchangeMenu((EntityPlayerMP) target);
                openExchangeMenu(sender);
            }
        }
    }

    public void processExchangeOperation(EntityPlayerMP playerMP, EnumExchangeOperation operation, long offeredCurrency) {
        UUID playerUUID = CommonReference.getPersistentUUID(playerMP);
        int index = OxygenHelperServer.getPlayerIndex(playerUUID);
        if (offeredCurrency != 0L) {
            if (offeredCurrency < 0L || !CurrencyHelperServer.enoughCurrency(playerUUID, offeredCurrency, OxygenMain.COMMON_CURRENCY_INDEX))
                operation = EnumExchangeOperation.CLOSE;
        }
        if (this.haveExchangeProcess(index))
            this.getExchangeProcess(index).processAction(index, operation, offeredCurrency);
    }

    public static void openExchangeMenu(EntityPlayerMP playerMP) {
        playerMP.openGui(ExchangeMain.instance, ExchangeGUIHandler.EXCHANGE_MENU, playerMP.world, (int) playerMP.posX, (int) playerMP.posY, (int) playerMP.posZ);                            
    }
}
