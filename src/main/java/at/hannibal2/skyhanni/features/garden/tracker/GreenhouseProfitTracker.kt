package at.hannibal2.skyhanni.features.garden.tracker

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.features.garden.greenhouse.GreenhouseProfitTrackerConfig
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.events.ItemAddEvent
import at.hannibal2.skyhanni.events.SackChangeEvent
import at.hannibal2.skyhanni.events.garden.farming.CropClickEvent
import at.hannibal2.skyhanni.features.garden.GardenApi
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NeuInternalName.Companion.toInternalName
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.RenderDisplayHelper
import at.hannibal2.skyhanni.utils.collection.RenderableCollectionUtils.addSearchString
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.Searchable
import at.hannibal2.skyhanni.utils.renderables.toSearchable
import at.hannibal2.skyhanni.utils.renderables.primitives.text
import at.hannibal2.skyhanni.utils.NumberUtil.shortFormat
import at.hannibal2.skyhanni.utils.tracker.ItemTrackerData
import at.hannibal2.skyhanni.utils.tracker.SessionUptime
import at.hannibal2.skyhanni.utils.tracker.SkyHanniItemTracker
import at.hannibal2.skyhanni.utils.tracker.SkyHanniTracker
import com.google.gson.annotations.Expose

@SkyHanniModule
object GreenhouseProfitTracker {

    private val config: GreenhouseProfitTrackerConfig get() = SkyHanniMod.feature.garden.greenhouse.greenhouseProfitTracker

    // Allowed raw crops that are obtained from breaking raw crops or mutations.
    private val allowedCrops = listOf(
        "NETHER_STALK",
        "WHEAT",
        "INK_SAC:3",
        "PUMPKIN",
        "MELON",
        "POTATO_ITEM",
        "CARROT_ITEM",
        "CACTUS",
        "SUGAR_CANE",
        "RED_MUSHROOM",
        "BROWN_MUSHROOM",
        "WILD_ROSE",
        "MOONFLOWER",
        "DOUBLE_PLANT"
    ).map { it.toInternalName() }

    val tracker = SkyHanniItemTracker(
        "Greenhouse Profit Tracker",
        ::Data,
        { it.garden.greenhouse.profitTracker },
        drawDisplay = { drawDisplay(it) },
        trackerConfig = { config.perTrackerConfig },
        customUptimeControl = true
    )

    class Data : ItemTrackerData<SessionUptime.Garden>(SessionUptime.Garden::class) {
        @Expose
        var brokenPlants: Long = 0

        @Expose
        var brokenMutations: Long = 0

        override fun getDescription(timesGained: Long): List<String> {
            return listOf(
                "§7Dropped §e${timesGained.addSeparators()} §7times."
            )
        }

        override fun getCoinName(item: TrackedItem) = "§6Greenhouse Coins"

        override fun getCoinDescription(item: TrackedItem): List<String> {
            val amountFormat = item.totalAmount.shortFormat()
            return listOf(
                "§7Greenhouse drops gave you",
                "§6$amountFormat coins §7in total.",
            )
        }
    }

    private fun drawDisplay(data: Data): List<Searchable> = buildList {
        addSearchString("§e§lGreenhouse Profit Tracker")

        val profit = tracker.drawItems(data, { true }, this)

        add(Renderable.text("§7Broken Plants: §e${data.brokenPlants.addSeparators()}").toSearchable())
        add(Renderable.text("§7Broken Mutations: §e${data.brokenMutations.addSeparators()}").toSearchable())

        val duration = data.getTotalUptime()
        addAll(tracker.addTotalProfit(profit, data.brokenPlants, "plant break", duration, "Breaks"))

        tracker.addPriceFromButton(this)
    }

    init {
        RenderDisplayHelper(
            outsideInventory = true,
            inOwnInventory = true,
            condition = { config.enabled && GardenApi.inGarden() },
            onRender = {
                tracker.firstUpdate()
                tracker.renderDisplay(config.position)
            },
        )
    }

    @HandleEvent(onlyOnIsland = IslandType.GARDEN)
    fun onItemAdd(event: ItemAddEvent) {
        if (!config.enabled || !GardenApi.inGarden()) return
        
        if (event.internalName in allowedCrops) {
            tracker.addItem(event.internalName, event.amount, command = false)
        }
    }

    @HandleEvent(onlyOnIsland = IslandType.GARDEN)
    fun onSackChange(event: SackChangeEvent) {
        if (!config.enabled || !GardenApi.inGarden()) return

        for (change in event.sackChanges) {
            val amount = change.delta
            if (amount > 0 && change.internalName in allowedCrops) {
                tracker.addItem(change.internalName, amount, command = false)
            }
        }
    }

    @HandleEvent(onlyOnIsland = IslandType.GARDEN)
    fun onCropClick(event: CropClickEvent) {
        if (!config.enabled || !GardenApi.inGarden()) return

        // Assuming every crop click that breaks something is a plant break.
        tracker.modify { it.brokenPlants++ }
        
        // TODO: Detect if it's a mutation block and increment brokenMutations
        // if (event.blockType.isMutation()) tracker.modify { it.brokenMutations++ }
    }
}
