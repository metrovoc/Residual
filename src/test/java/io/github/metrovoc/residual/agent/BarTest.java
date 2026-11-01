package io.github.metrovoc.residual.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The tool layer against the tools it has to match, list by list: each name in a reference tool's list is written in the
 * language, and has to read and call only what exists. What each does in the world is played by the tool layer's scripted run.
 */
class BarTest {
    /** Every module but the creative one, which only a creative player has. */
    private static final String[][] ROUTERS = {
        {"activator", "use at front"},
        {"activator on an entity", "attack at entities(5, zombie)"},
        {"breaker", "dig at front"},
        {"detector", "emit count(hands, coal)"},
        {"distributor", "send coal from hands to each marks()"},
        {"dropper", "drop 4 coal at front"},
        {"energy distributor", "charge from front to each marks()"},
        {"energy output", "charge from hands to front"},
        {"extruder", "extrude 8 stone at up, retract 8 at up"},
        {"extruder with a template", "for each $p in line(front, offset(front, north, 7)) (place stone at $p)"},
        {"flinger", "fling 4 coal at up speed 1 pitch 45 yaw 90"},
        {"fluid", "pump water from front to hands"},
        {"fluid to a target", "pump from hands to 1"},
        {"placer", "place stone at front"},
        {"player", "send from section(armor) to hands"},
        {"puller", "send from front to hands"},
        {"puller at a target", "send from 1 to hands"},
        {"sender", "send from hands to front"},
        {"sender at a target", "send from hands to 1"},
        {"sender across dimensions", "send from hands to 1"},
        {"vacuum", "pickup at near(12)"},
        {"vacuum for xp", "pickup xp"},
        {"void", "void 8 cobblestone"},
    };

    private static final String[][] DRONES = {
        {"comment", "note the north field"},
        {"start", "set $started 1"},
        {"area", "set $a box(front, up) - sphere(1, 2)"},
        {"text", "set $t \"words\""},
        {"item_filter", "send iron ingot or tag(logs) except mod(residual) where durability($it) > 3 from front to hands"},
        {"item_assign", "set $i item(coal)"},
        {"liquid_filter", "pump water or lava except milk from front to hands"},
        {"coordinate", "set $p pos(1, 2, 3)"},
        {"coordinate_operator", "set $p offset(here, north, 3), set $p pos(x(here) + 1, y(here), z(here))"},
        {"entity_attack", "attack at entities(8, zombie)"},
        {"dig", "dig stone at box(front, up)"},
        {"harvest", "harvest at near(8)"},
        {"place", "place stone at line(front, up)"},
        {"block_right_click", "use iron hoe at front sneak"},
        {"entity_right_click", "use shears at entities(4, sheep)"},
        {"pickup_item", "pickup coal at near(8)"},
        {"drop_item", "drop 4 coal at up"},
        {"void_item", "void cobblestone"},
        {"void_liquid", "drain 1000 lava"},
        {"inventory_export", "send coal from hands to front"},
        {"inventory_import", "send coal from front to hands"},
        {"liquid_export", "pump from hands to front"},
        {"liquid_import", "pump from front to hands"},
        {"entity_export", "lower"},
        {"entity_import", "lift at entities(4, other_than(player))"},
        {"rf_import", "charge from front to hands"},
        {"rf_export", "charge from hands to front"},
        {"goto", "go 1"},
        {"teleport", "teleport 1"},
        {"emit_redstone", "emit 15, pulse 15 for 5"},
        {"label", "to patrol (go 1, go 2)"},
        {"jump", "to patrol (go 1, patrol)"},
        {"jump_sub", "to patrol (go 1), patrol"},
        {"wait", "wait 5"},
        {"rename", "rename Scout"},
        {"suicide", "retire"},
        {"external_program", "run at front"},
        {"crafting", "craft 8 torch"},
        {"standby", "stay"},
        {"logistics", "deliver"},
        {"for_each_coordinate", "for each $p in box(front, up) (dig at $p)"},
        {"for_each_item", "for each $i in items(front) (say $i)"},
        {"edit_sign", "sign hello at front"},
        {"condition_coordinate", "if x(here) > x(1) (stop)"},
        {"condition_redstone", "if any(list(box(front, up)), redstone($it) > 0) (stop)"},
        {"condition_light", "if light(up) > 7 (stop)"},
        {"condition_item_inventory", "if count(front, coal) >= 16 (stop)"},
        {"condition_block", "if all(list(box(front, up)), has_block($it)) (stop)"},
        {"condition_liquid_inventory", "if fluid_amount(front) > 1000 (stop)"},
        {"condition_entity", "if size(entities(8, zombie)) > 0 (stop)"},
        {"condition_pressure", "if energy(front) > 1000 (stop)"},
        {"condition_item", "if passes(tag(coals), item(coal)) (stop)"},
        {"drone_condition_item", "if count(hands, coal) > 0 (stop)"},
        {"drone_condition_liquid", "if fluid_amount(hands) > 0 (stop)"},
        {"drone_condition_entity", "if carrying() > 0 (stop)"},
        {"drone_condition_pressure", "if fuel() > 1000 (stop)"},
        {"drone_condition_upgrades", "if upgrades(speed) > 1 (stop)"},
        {"condition_rf", "if energy(front) > 0 (stop)"},
        {"drone_condition_rf", "if fuel() > 0 (stop)"},
        {"computer_control", "on change #go (go #go)"},
    };

    private static final String[][] TURTLE = {
        {"forward", "forward"}, {"back", "back"}, {"up", "up"}, {"down", "down"}, {"turnLeft", "turn left"}, {"turnRight", "turn right"},
        {"dig", "dig at front"}, {"digUp", "dig at up"}, {"digDown", "dig at down"},
        {"place", "place stone at front"}, {"placeUp", "place stone at up"}, {"placeDown", "place stone at down"},
        {"drop", "drop at front"}, {"dropUp", "drop at up"}, {"dropDown", "drop at down"},
        {"select", "select 3"},
        {"getItemCount", "set $n slot_count(3)"}, {"getItemSpace", "set $n slot_space(3)"},
        {"detect", "set $b detect(front)"}, {"detectUp", "set $b detect(up)"}, {"detectDown", "set $b detect(down)"},
        {"compare", "set $b compare(front)"}, {"compareUp", "set $b compare(up)"}, {"compareDown", "set $b compare(down)"},
        {"attack", "attack at front"}, {"attackUp", "attack at up"}, {"attackDown", "attack at down"},
        {"suck", "send from front to hands"}, {"suckUp", "send from up to hands"}, {"suckDown", "send from down to hands"},
        {"getFuelLevel", "set $n fuel()"}, {"refuel", "refuel 8 coal"},
        {"compareTo", "set $b compare_slots(3)"}, {"transferTo", "transfer 8 to 3"},
        {"getSelectedSlot", "set $n selected()"}, {"getFuelLimit", "set $n fuel_limit()"},
        {"equipLeft", "equip diamond pickaxe"}, {"equipRight", "equip shears"},
        {"getEquippedLeft", "set $i equipped()"}, {"getEquippedRight", "set $i equipped()"},
        {"inspect", "set $b block(front)"}, {"inspectUp", "set $b block(up)"}, {"inspectDown", "set $b block(down)"},
        {"getItemDetail", "set $i slot_item(3), set $n name($i), set $d damage($i)"},
        {"craft", "craft 4 stick"},
    };

    private static final List<String> INSTRUMENTS = List.of("harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar", "chime", "xylophone", "iron_xylophone",
            "cow_bell", "didgeridoo", "bit", "banjo", "pling", "zombie", "skeleton", "creeper", "dragon", "wither_skeleton", "piglin", "custom_head");

    /** Read aspects, by the reader they are on. */
    private static Map<String, String> aspects() {
        Map<String, String> m = new LinkedHashMap<>();
        for (String instrument : INSTRUMENTS) {
            m.put("audio " + instrument, "heard(" + instrument + ", 32)");
        }
        // block
        m.put("has block", "has_block(front)");
        m.put("dimension", "world_name(1)");
        m.put("x", "x(front)");
        m.put("y", "y(front)");
        m.put("z", "z(front)");
        m.put("block", "block(front)");
        m.put("tile entity nbt", "block_data(front)");
        m.put("biome", "biome(front)");
        m.put("block light", "light(front)");
        // entity
        m.put("item frame rotation", "frame_rotation(front)");
        m.put("entities", "entities(8)");
        m.put("players", "players(8)");
        m.put("entity", "entity(front)");
        m.put("item frame", "frame_item(front)");
        // extra-dimensional
        m.put("random", "random(100)");
        m.put("player count", "player_count()");
        m.put("server tick time", "tick_time()");
        m.put("server tps", "tps()");
        m.put("server players", "players()");
        // fluid
        m.put("tank full", "tank_full(front)");
        m.put("tank empty", "tank_empty(front)");
        m.put("tank not empty", "not tank_empty(front)");
        m.put("is tank", "is_tank(front)");
        m.put("fluid amount", "fluid_amount(front, 2)");
        m.put("total fluid amount", "fluid_amount(front)");
        m.put("fluid capacity", "fluid_capacity(front, 2)");
        m.put("total fluid capacity", "fluid_capacity(front)");
        m.put("tanks", "tanks(front)");
        m.put("fluid fill ratio", "fluid_fill(front, 2)");
        m.put("tank fluids", "fluids(front)");
        m.put("tank capacities", "map(range(1, tanks(front)), fluid_capacity(front, $it))");
        m.put("tank fluid", "fluid(front, 2)");
        m.put("block fluid", "block_fluid(front)");
        // inventory
        m.put("inventory full", "inventory_full(front)");
        m.put("inventory empty", "inventory_empty(front)");
        m.put("inventory not empty", "not inventory_empty(front)");
        m.put("is inventory", "is_inventory(front)");
        m.put("inventory count", "count(front)");
        m.put("slots", "slots(front)");
        m.put("slots filled", "slots_used(front)");
        m.put("fill ratio", "fill(front)");
        m.put("items", "items(front)");
        m.put("slot item", "slot(front, 3)");
        // machine
        m.put("is worker", "is_worker(front)");
        m.put("has work", "has_work(front)");
        m.put("can work", "can_work(front)");
        m.put("is working", "working(front)");
        m.put("has temperature", "has_temperature(front)");
        m.put("temperature", "temperature(front)");
        m.put("maximum temperature", "max_temperature(front)");
        m.put("minimum temperature", "min_temperature(front)");
        m.put("default temperature", "min_temperature(front)");
        m.put("is recipe handler", "size(recipes(front)) > 0");
        m.put("recipes", "recipes(front)");
        m.put("recipe output by input", "recipe_output(item(raw iron), front)");
        m.put("recipe inputs by output", "recipe_inputs(item(iron ingot), front)");
        m.put("recipes by input", "recipes_using(item(raw iron), front)");
        m.put("recipes by output", "recipes_making(item(iron ingot), front)");
        m.put("recipe by input", "first(recipes_using(item(raw iron), front))");
        m.put("recipe by output", "first(recipes_making(item(iron ingot), front))");
        m.put("is energy handler", "is_energy(front)");
        m.put("can extract energy", "can_extract(front)");
        m.put("can insert energy", "can_insert(front)");
        m.put("is energy buffer full", "energy_full(front)");
        m.put("is energy buffer empty", "energy_empty(front)");
        m.put("is energy buffer not empty", "not energy_empty(front)");
        m.put("stored energy", "energy(front)");
        m.put("energy capacity", "energy_capacity(front)");
        m.put("energy fill ratio", "energy_fill(front)");
        // network
        m.put("is network", "is_network(front)");
        m.put("elements", "network_blocks(front)");
        m.put("energy batteries", "power_cells(front)");
        m.put("network energy stored", "power_stored(front)");
        m.put("network energy capacity", "power_capacity(front)");
        m.put("energy consumption rate", "power_drawn(front)");
        m.put("value", "read_var(front, \"stock\")");
        m.put("variable value by id", "#stock");
        // redstone
        m.put("redstone high", "signal_high(front)");
        m.put("redstone low", "signal_low(front)");
        m.put("redstone", "signal(front) > 0");
        m.put("redstone clock", "clock(5)");
        m.put("redstone level", "signal(front)");
        m.put("comparator", "comparator(front)");
        // world
        m.put("weather clear", "is_clear()");
        m.put("weather rain", "is_rain()");
        m.put("weather thunder", "is_thunder()");
        m.put("is day", "is_day()");
        m.put("is night", "is_night()");
        m.put("rain countdown", "rain_time()");
        m.put("world tick time", "tick_time()");
        m.put("day time", "time()");
        m.put("world light", "light(here)");
        m.put("world tps", "tps()");
        m.put("time", "time()");
        m.put("total time", "total_time()");
        m.put("world name", "world_name()");
        m.put("world players", "players()");
        // what the network holds
        m.put("network item count", "network_used()");
        m.put("network item max count", "network_capacity()");
        m.put("network items", "network_items()");
        m.put("network count of item", "network_count(iron ingot)");
        m.put("network item interfaces", "network_ports()");
        m.put("network fluid count", "network_fluid_used()");
        m.put("network fluid max count", "network_fluid_capacity()");
        m.put("network fluids", "network_fluids()");
        m.put("network count of fluid", "network_fluid_count(water)");
        m.put("network fluid interfaces", "network_ports()");
        // what it can make
        m.put("crafting recipes", "craftable()");
        m.put("active crafting jobs", "jobs()");
        m.put("crafting ingredients", "network_lacks()");
        return m;
    }

    private static final Pattern CALL = Pattern.compile("([a-z_]+)\\(");

    private static List<String> unwritable(String text) {
        List<String> wrong = new ArrayList<>();
        Program.Parsed parsed = Program.parse(text);
        if (!parsed.ok()) {
            wrong.add(text + ": " + parsed.problem().key() + " " + parsed.problem().word());
        }
        Matcher m = CALL.matcher(text);
        while (m.find()) {
            if (Ops.fn(m.group(1)) == null) {
                wrong.add(text + ": no " + m.group(1));
            }
        }
        return wrong;
    }

    private static void written(String[][] list) {
        List<String> wrong = new ArrayList<>();
        for (String[] row : list) {
            wrong.addAll(unwritable(row[1]));
        }
        assertEquals(List.of(), wrong);
    }

    @Test
    void everyModuleOfTheRouters() {
        written(ROUTERS);
    }

    @Test
    void everyPieceOfTheDrones() {
        assertEquals(60, DRONES.length);
        written(DRONES);
    }

    @Test
    void everyFunctionOfTheTurtles() {
        assertEquals(45, TURTLE.length);
        written(TURTLE);
    }

    @Test
    void everyReadAspectOfTheReaders() {
        Map<String, String> aspects = aspects();
        assertEquals(133, aspects.size());
        List<String> wrong = new ArrayList<>();
        aspects.forEach((name, expr) -> wrong.addAll(unwritable("set $v " + expr)));
        assertEquals(List.of(), wrong);
    }

    @Test
    void theNumbersOfAFullyUpgradedBody() {
        int interval = Upgrades.BASE_INTERVAL >> Upgrades.Kind.SPEED.most;
        assertEquals(1, interval, "an operation a tick, where a turtle takes 8");
        assertEquals(1280, 20 * (Upgrades.BASE_ITEMS << Upgrades.Kind.STACK.most) / interval, "items a second for each program");
        assertTrue(20 * (Upgrades.BASE_FLUID << Upgrades.Kind.FLUID.most) / interval >= 20_000, "mB a second");
        assertTrue(Upgrades.BASE_ENERGY << 2 * Upgrades.Kind.ENERGY.most >= 64_000, "FE a run");
        assertEquals(48, Upgrades.BASE_REACH + 8 * Upgrades.Kind.RANGE.most, "blocks of reach");
        assertTrue(Agent.MOST_CARRIED > 36, "more than a drone carries");
        assertEquals(250_000, Area.MOST);
        assertEquals(9, PortWork.CARDS, "programs on one Port");
    }
}
