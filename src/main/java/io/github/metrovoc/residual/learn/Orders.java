package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The language of orders: many ways of saying what an agent can do. A model reads {@code > what was said} and writes
 * {@code = the program meant}, in the language agents run ({@code fetch bread}, {@code harvest, put wheat},
 * {@code wait until night, home}, {@code dig at box(mark(1), mark(2))}); an agent runs only what its model wrote. A program can be
 * longer than the window, so the order stays in view and what has been written goes out of it a stretch at a time
 * ({@link Sampler#window}). A model is scored on whole programs: the share it would write exactly as the language writes them.
 */
public final class Orders {
    public record Example(String said, String meant) {
    }

    /** The longest order a model is shown. */
    public static final int LONGEST = 48;
    /** The longest program a model writes for an order. */
    public static final int WRITTEN = 120;
    /** How many orders a test takes, each written out whole. */
    private static final int TESTED = 256;
    /** The share of orders that come from what the full language lets people say, the rest being the first things people ask. */
    private static final float FULL_SHARE = 0.6f;

    /** One step said a way, and the step meant. In both, %1$s stands for a thing or words, %2$s for a number. */
    private record Way(String said, String meant) {
    }

    private enum Arg { NONE, THING, SEED, BLOCK, WORDS, SECONDS, MARK, COUNT }

    /** Ways of saying one kind of step, and what each needs filled in. */
    private record Step(Arg arg, boolean endless, List<Way> ways) {
    }

    private static List<Way> ways(String meant, String... said) {
        return java.util.Arrays.stream(said).map(s -> new Way(s, meant)).toList();
    }

    private static final Step FARM = new Step(Arg.NONE, true, ways("repeat (farm)",
            "farm", "go farm", "go and farm", "tend the fields", "tend the crops", "look after the crops",
            "look after the fields", "work the fields", "work the farm", "see to the crops", "keep the fields",
            "back to the fields", "go to the fields", "go work the farm", "take care of the farm", "do the farming",
            "time to farm", "the crops need you", "watch the fields", "mind the farm"));
    private static final Step STORE = new Step(Arg.NONE, true, ways("repeat (store)",
            "store", "store everything", "tidy the stores", "tidy the chests", "tidy up", "clean up the chests",
            "empty the barrels", "empty the barrel", "file everything", "stock the shelves", "keep the stores tidy",
            "mind the stores", "keep the storeroom tidy", "keep things where they were"));
    private static final Step SORT = new Step(Arg.NONE, true, ways("repeat (sort)",
            "sort", "sort everything", "sort the chests", "sort the storage", "sort the stores", "go sort things",
            "sort it out", "do the sorting", "sort things by kind", "sort the barrels", "keep things sorted",
            "sort the loot", "sort by kind", "put each thing with its kind", "file things by kind"));
    private static final Step BUILD = new Step(Arg.NONE, true, ways("repeat (build)",
            "build", "keep building", "carry on building", "go on building", "finish the wall", "finish the row",
            "continue the pattern", "continue the wall", "build the rest", "build the rest of the row",
            "go on with the wall", "lay the next blocks", "follow the pattern", "keep the pattern going", "build on",
            "extend the wall", "add to the wall", "back to building"));
    private static final Step COPY = new Step(Arg.NONE, false, ways("copy",
            "copy", "copy the build", "copy it", "copy the house", "make a copy", "build a copy", "make another one",
            "build another one", "copy what i marked", "duplicate the build", "copy the marked build", "copy the tower",
            "build it again over there", "make one more like it", "copy the hut"));
    private static final Step MOVE = new Step(Arg.NONE, false, ways("move",
            "move the build", "move it over", "move the house", "move what i marked", "carry the build over",
            "shift the build", "move it over there", "move the hut", "move the tower", "pick the build up and move it",
            "relocate the build", "move everything over", "move the marked build"));
    private static final Step HAUL = new Step(Arg.NONE, true, ways("repeat (haul)",
            "haul", "go haul", "haul the goods", "run the errand", "do the errand", "do the run", "make the run",
            "make the trip", "go on your errand", "bring the goods back", "bring back the load", "fetch the load",
            "go to the pickup", "carry the goods home", "start hauling", "do your rounds", "go collect"));
    private static final Step STAY = new Step(Arg.NONE, true, ways("stay",
            "stay", "stay here", "stay put", "wait", "wait here", "wait for me", "stop", "stop there", "hold on",
            "hold still", "stand still", "do not move", "dont move", "halt", "rest", "take a rest", "sit tight",
            "wait right there", "stay where you are"));
    private static final Step FOLLOW = new Step(Arg.NONE, false, ways("follow",
            "follow me", "follow", "come with me", "come along", "walk with me", "keep up with me", "stay close",
            "with me", "stick with me", "tag along", "keep close", "on my heels"));
    private static final Step COME = new Step(Arg.NONE, false, ways("come",
            "come", "come here", "come to me", "over here", "here please", "this way", "come on", "to me",
            "come over here", "come find me", "get over here", "come back here", "i need you here"));
    private static final Step HOME = new Step(Arg.NONE, false, ways("home",
            "home", "go home", "go back home", "return home", "head home", "back to your post", "go back to your post",
            "back home", "go back", "go to your home", "return to your post", "off you go home", "back to base"));
    private static final Step HARVEST = new Step(Arg.NONE, false, ways("harvest",
            "harvest", "harvest the field", "harvest the crops", "harvest the wheat", "go harvest", "reap the field",
            "reap the wheat", "pick the crops", "bring in the harvest", "gather the crops", "cut the wheat",
            "pick what is ripe", "harvest what is ripe"));
    private static final Step TILL = new Step(Arg.NONE, false, ways("till",
            "till", "till the soil", "till the ground", "hoe the ground", "hoe the soil", "make farmland",
            "plough the field", "plow the field", "turn the soil", "dig the field", "get the soil ready",
            "make a field"));
    private static final Step PICKUP = new Step(Arg.NONE, false, ways("pickup",
            "pick up", "pick it up", "pick that up", "pick up the items", "pick up the drops", "collect the drops",
            "gather the loot", "grab what is on the ground", "clean up the floor", "pick up everything",
            "collect what is lying around", "get the stuff on the ground"));
    private static final Step PUT = new Step(Arg.NONE, false, ways("put",
            "put this away", "put these away", "put the things away", "put everything away", "put your things away",
            "put it in the chest", "put it all in the chest", "unload", "unload your things", "empty your hands",
            "drop off what you carry", "drop it off", "store what you carry", "put what you have away"));
    private static final Step PUT_THING = new Step(Arg.THING, false, ways("put %1$s",
            "put the %1$s away", "put away the %1$s", "store the %1$s", "put the %1$s in the chest",
            "put your %1$s away", "drop off the %1$s", "unload the %1$s", "stash the %1$s"));
    private static final Step FETCH = new Step(Arg.THING, false, ways("fetch %1$s",
            "bring me %1$s", "bring me some %1$s", "bring me the %1$s", "fetch %1$s", "fetch me %1$s",
            "fetch me some %1$s", "fetch the %1$s", "get me %1$s", "get me some %1$s", "go get %1$s",
            "go get some %1$s", "go and get the %1$s", "go and fetch %1$s", "i need %1$s", "i need some %1$s",
            "i need the %1$s", "i want %1$s", "i want some %1$s", "can you bring me %1$s", "can you get me some %1$s",
            "could you fetch %1$s", "could you bring the %1$s", "please bring %1$s", "please get me %1$s",
            "%1$s please", "some %1$s please", "find me some %1$s", "find the %1$s", "we need %1$s",
            "we need more %1$s", "hand me the %1$s", "hand me some %1$s", "grab me %1$s", "grab some %1$s",
            "get the %1$s from the chest", "bring the %1$s over here", "carry some %1$s to me", "bring %1$s here",
            "go find %1$s", "i could use some %1$s", "go fetch the %1$s for me", "run and get some %1$s",
            "fetch %1$s from storage", "i am out of %1$s", "we are out of %1$s", "get %1$s", "bring %1$s"));
    private static final Step FETCH_SOME = new Step(Arg.COUNT, false, ways("fetch %2$s %1$s",
            "bring me %2$s %1$s", "fetch me %2$s %1$s", "get me %2$s %1$s", "i need %2$s %1$s", "grab me %2$s %1$s",
            "%2$s %1$s please", "can you bring me %2$s %1$s", "go get %2$s %1$s", "fetch %2$s %1$s",
            "i want %2$s %1$s", "hand me %2$s %1$s"));
    private static final Step PLANT = new Step(Arg.SEED, false, ways("plant %1$s",
            "plant %1$s", "plant the %1$s", "plant some %1$s", "sow the %1$s", "sow %1$s", "sow some %1$s",
            "put the %1$s in the ground", "plant the field with %1$s", "get the %1$s planted", "plant out the %1$s"));
    private static final Step MINE = new Step(Arg.BLOCK, false, ways("mine %1$s",
            "mine some %1$s", "mine the %1$s", "mine %1$s", "dig up some %1$s", "dig out the %1$s", "break the %1$s",
            "break some %1$s", "go mine %1$s", "quarry some %1$s"));
    private static final Step WAIT = new Step(Arg.SECONDS, false, ways("wait %2$s",
            "wait %2$s seconds", "wait for %2$s seconds", "hold on %2$s seconds", "pause for %2$s seconds",
            "give it %2$s seconds", "wait %2$s"));
    private static final Step GO = new Step(Arg.MARK, false, ways("go %2$s",
            "go to mark %2$s", "go to waymark %2$s", "walk to mark %2$s", "head to mark %2$s", "move to mark %2$s",
            "go over to mark %2$s", "stand on mark %2$s", "go stand at mark %2$s"));
    private static final Step SAY = new Step(Arg.WORDS, false, ways("say %1$s",
            "say %1$s", "say %1$s out loud", "tell everyone %1$s", "shout %1$s", "announce %1$s", "call out %1$s"));

    /** Every single step, the commonest first in the draw: fetching. */
    private static final List<Step> SINGLE = List.of(FETCH, FETCH_SOME, FARM, STORE, SORT, BUILD, COPY, MOVE, HAUL, STAY, FOLLOW, COME,
            HOME, HARVEST, TILL, PICKUP, PUT, PUT_THING, PLANT, MINE, WAIT, GO, SAY);
    /** Steps that end, which may be put one after another. */
    private static final List<Step> FINITE = List.of(FETCH, FETCH_SOME, COME, HOME, HARVEST, TILL, PICKUP, PUT,
            PUT_THING, PLANT, MINE, WAIT, GO, SAY);
    private static final List<String> THEN = List.of("%s and %s", "%s then %s", "%s, then %s", "first %s, then %s",
            "%s and then %s", "%s, and %s");

    /** Said with what a step works on and where, in the language's clauses. */
    private static final List<Way> MOVING = wide(
            ways("send {n} {t} from {a} to {b}", "move {n} {t} from mark {a} to mark {b}", "carry {n} {t} from mark {a} to mark {b}",
                    "take {n} {t} from mark {a} to mark {b}"),
            ways("send {t} from {a} to {b}", "move the {t} from mark {a} to mark {b}", "move all the {t} from mark {a} to mark {b}",
                    "send the {t} from mark {a} to mark {b}", "carry the {t} from mark {a} to mark {b}"),
            ways("send from {a} to {b}", "empty mark {a} into mark {b}", "move everything from mark {a} to mark {b}",
                    "move it all from mark {a} to mark {b}"),
            ways("send {t} from front to network", "put the {t} from that chest in the network", "send the {t} here to the network",
                    "put the {t} in that chest on the network"),
            ways("send from front to network", "empty that chest into the network", "put that chest on the network",
                    "put everything in that chest on the network"),
            ways("send {n} {t} from network to hands", "take {n} {t} out of the network", "get {n} {t} from the network",
                    "pull {n} {t} off the network"),
            ways("send from hands to network", "put what you hold on the network", "empty your hands onto the network"),
            ways("send {t} from network to {a}", "fill mark {a} with {t} from the network", "stock mark {a} with {t} from the network"),
            ways("send {t} from hands to each marks()", "share the {t} out among the marks", "hand out the {t} to every mark",
                    "give every mark some of the {t}"),
            ways("pump {f} from {a} to {b}", "pump the {f} from mark {a} to mark {b}", "pour the {f} from mark {a} to mark {b}"),
            ways("pump {f} from {a} to hands", "fill your tank with {f} from mark {a}", "take the {f} from mark {a}"),
            ways("pump {f} from front to network", "put the {f} on the network", "store the {f} on the network"),
            ways("drain {f}", "get rid of the {f}", "empty your tank of {f}"),
            ways("charge from {a} to hands", "charge up from mark {a}", "take power from mark {a}"),
            ways("charge from hands to {a}", "power mark {a}", "give power to mark {a}", "charge mark {a}"),
            ways("charge {n} from {a} to {b}", "move {n} power from mark {a} to mark {b}"));

    private static final List<Way> ACTING = wide(
            ways("dig at front", "dig the block in front of you", "break what is in front of you"),
            ways("dig at up", "dig out what is above you", "break the block above you"),
            ways("dig at down", "dig below you", "dig under your feet"),
            ways("dig at {a}", "dig at mark {a}", "break the block at mark {a}"),
            ways("dig {s} at {a}", "break the {s} at mark {a}", "dig the {s} at mark {a}"),
            ways("dig {n} {s} at near(8)", "dig {n} {s} around you", "dig up {n} {s} nearby"),
            ways("place {s} at {a}", "place {s} at mark {a}", "put a block of {s} at mark {a}"),
            ways("place {s} at up", "put {s} above you", "place {s} over your head"),
            ways("place {s} at front", "put {s} in front of you", "place {s} ahead of you"),
            ways("use at {a}", "right click mark {a}", "flip the lever at mark {a}", "press the button at mark {a}"),
            ways("use at front", "right click what is in front of you", "use what is in front of you"),
            ways("use {o} at {a}", "use the {o} on mark {a}", "right click mark {a} with the {o}"),
            ways("use shears at entities(8, {l})", "shear the {l} nearby", "shear the {l}"),
            ways("use wheat at entities(8, {l})", "feed the {l}", "feed the {l} nearby"),
            ways("attack at entities(8, {m})", "attack the {m} nearby", "kill the {m}", "fight the {m}"),
            ways("drop {n} {t} at front", "drop {n} {t}", "drop {n} {t} in front of you"),
            ways("fling {n} {t} at front speed 1", "throw {n} {t}", "throw {n} {t} ahead"),
            ways("void {t}", "destroy the {t}", "get rid of the {t}"),
            ways("void {n} {t}", "destroy {n} {t}", "throw away {n} {t}"),
            ways("pickup at near(8)", "pick up what lies around you", "vacuum up the drops around you"),
            ways("pickup {t} at near(8)", "pick up the {t} around you", "collect the {t} nearby"),
            ways("pickup xp", "collect the xp", "pick up the experience"),
            ways("emit 15", "give out a signal", "turn your signal on"),
            ways("emit 0", "stop the signal", "turn your signal off"),
            ways("pulse 15 for 5", "send a pulse", "give a short pulse"),
            ways("extrude 8 {s} at front", "build a bridge of {s}", "lay a bridge of {s} ahead"),
            ways("extrude {n} {s} at up", "build a pillar of {n} {s}", "stack {n} {s} up"),
            ways("retract 8 at front", "take the bridge back", "pull the bridge in"),
            ways("sign {w} at front", "write {w} on the sign", "put {w} on that sign"),
            ways("forward {n}", "walk {n} blocks forward", "go forward {n}"),
            ways("turn left", "turn left", "face left"),
            ways("turn right", "turn right", "face right"),
            ways("turn around", "turn around", "face the other way"),
            ways("teleport {a}", "teleport to mark {a}", "jump to mark {a}"),
            ways("select {n}", "select slot {n}", "use slot {n}"),
            ways("equip {o}", "equip the {o}", "hold the {o}"),
            ways("refuel coal", "burn coal for fuel", "refuel on coal"),
            ways("lift at entities(4, {l})", "lift the {l}", "pick up the {l}"),
            ways("lower", "put it down", "set it down"),
            ways("run at front", "run the card in that chest", "run the program in that chest"),
            ways("retire", "pack yourself up", "go back to being an item"),
            ways("craft {n} {t}", "craft {n} {t}", "make {n} {t}"));

    private static final List<Way> AREAS = wide(
            ways("dig at box(mark(1), mark(2))", "dig out everything between the marks", "quarry the marked area", "mine out the marked area",
                    "clear the marked area"),
            ways("dig at box(mark({a}), mark({b}))", "clear from mark {a} to mark {b}", "dig out from mark {a} to mark {b}",
                    "dig the box from mark {a} to mark {b}"),
            ways("dig {s} at box(mark({a}), mark({b}))", "dig the {s} from mark {a} to mark {b}", "mine the {s} from mark {a} to mark {b}"),
            ways("dig {s} at box(mark(1), mark(2))", "dig all the {s} between the marks", "mine the {s} in the marked area"),
            ways("place {s} at box(mark(1), mark(2))", "fill the marked area with {s}", "fill between the marks with {s}",
                    "build a floor of {s} between the marks"),
            ways("place {s} at box(mark({a}), mark({b}))", "fill mark {a} to mark {b} with {s}"),
            ways("place {s} at wall(mark(1), mark(2), y)", "build a wall of {s} between the marks", "wall off the marks with {s}"),
            ways("place {s} at wall(mark({a}), mark({b}), y)", "build a {s} wall from mark {a} to mark {b}"),
            ways("place {s} at line(mark({a}), mark({b}))", "lay {s} from mark {a} to mark {b}", "lay a line of {s} from mark {a} to mark {b}"),
            ways("dig at sphere(mark({a}), mark({b}))", "dig a ball from mark {a} out to mark {b}", "hollow out a ball at mark {a} to mark {b}"),
            ways("place {s} at sphere(mark({a}), mark({b}), hollow)", "build a shell of {s} from mark {a} to mark {b}"),
            ways("place {s} at pyramid(mark(1), mark(2), y)", "build a pyramid of {s} between the marks", "raise a {s} pyramid on the marks"),
            ways("dig at cylinder(mark({a}), mark({b}), filled, y)", "dig a round shaft from mark {a} to mark {b}", "sink a shaft from mark {a} to mark {b}"),
            ways("place {s} at torus(mark({a}), mark({b}), y)", "build a ring of {s} from mark {a} to mark {b}"),
            ways("place {s} at grid(mark(1), mark(2), {n})", "place {s} every {n} blocks between the marks", "put {s} every {n} blocks in the area"),
            ways("dig at box(mark(1), mark(2)) - box(mark(3), mark(4))", "dig the first box but not the second", "clear marks 1 and 2 but not 3 and 4"),
            ways("harvest at near({n})", "harvest everything within {n} blocks", "harvest within {n} blocks"),
            ways("plant {d} at near({n})", "plant {d} within {n} blocks", "sow {d} within {n} blocks"),
            ways("till at box(mark(1), mark(2))", "till the ground between the marks", "till the marked area"),
            ways("harvest at box(mark(1), mark(2))", "harvest the marked area", "harvest between the marks"));

    private static final List<Way> FILTERING = wide(
            ways("send tag({g}) from front to network", "put all the {g} on the network", "move every kind of {g} to the network",
                    "put all the {g} in that chest on the network"),
            ways("send tag({g}) from {a} to {b}", "move all the {g} from mark {a} to mark {b}", "move every kind of {g} from mark {a} to mark {b}"),
            ways("send except {t} from front to network", "move everything but the {t} to the network", "empty that chest but keep the {t}",
                    "put all but the {t} on the network"),
            ways("send except {t} from {a} to {b}", "move all but the {t} from mark {a} to mark {b}", "empty mark {a} into {b} but keep the {t}"),
            ways("send {t} or {u} from front to network", "move the {t} and the {u} to the network", "put {t} and {u} on the network"),
            ways("send {t} or {u} from {a} to {b}", "move {t} and {u} from mark {a} to mark {b}"),
            ways("send regex(\"_ore$\") from front to network", "put all the ore on the network", "move anything that is an ore to the network"),
            ways("send mod(minecraft) from front to network", "put the vanilla things on the network", "move what is from minecraft to the network"),
            ways("send kind({k}) from front to network", "put the {k} on the network", "move the {k} to the network", "empty the {k} into the network"),
            ways("send kind({k}) from {a} to {b}", "move the {k} from mark {a} to mark {b}"),
            ways("send where durability($it) < 50 from front to hands", "take the worn tools from that chest", "take what is nearly broken from that chest"),
            ways("send where food($it) > 0 from front to hands", "take anything you can eat from that chest", "take the food from that chest"),
            ways("attack at entities(8, other_than({l}))", "attack anything but the {l}", "kill everything but the {l}"));

    private static final List<Way> EACH = wide(
            ways("for each $p in marks() (go $p)", "go to every mark", "visit each mark in turn", "walk to each mark"),
            ways("for each $p in marks() (dig at $p)", "dig at every mark", "break the block at each mark"),
            ways("for each $p in marks() (place {s} at $p)", "place {s} at every mark", "put {s} at each mark"),
            ways("for each $p in marks() (use at $p)", "right click every mark", "use each mark in turn"),
            ways("for each $p in marks() (send {t} from $p to hands)", "take the {t} from each mark", "collect the {t} from every mark"),
            ways("for each $p in marks() (send {t} from hands to $p)", "put {t} in each mark", "leave some {t} at every mark"),
            ways("for each $p in marks() (go $p, say {w})", "go to each mark and say {w}", "visit each mark and say {w}"),
            ways("for each $i in items(front) (say $i)", "say what is in that chest", "tell me what is in that chest"),
            ways("for each $e in entities(8, {m}) (attack at $e)", "attack each {m} in turn", "fight the {m} one by one"));

    private static final List<Way> OWN_STEPS = wide(
            ways("to {c} (dig at front, pickup), {c}", "make a step {c} that digs and picks up", "teach yourself {c}: dig, then pick up"),
            ways("to {c} (harvest, put), {c}", "make a step {c} that harvests and puts away", "teach yourself {c}: harvest, then put away"),
            ways("to {c} (go {a}, come), {c}", "make a step {c}: go to mark {a}, then come", "call it {c}: go to mark {a} and come back"),
            ways("to {c} (fetch {t}), repeat 2 ({c})", "call it {c}: fetch {t}, and do it twice", "make a step {c} to fetch {t}, do it twice"),
            ways("to {c} (say {w}), repeat {n} ({c})", "call it {c}: say {w}, and do it {n} times"),
            ways("to {c} $p (go $p, dig at $p), {c} {a}", "make a step {c} to go and dig a place, try {a}", "call it {c}: go and dig, starting at {a}"));

    private static final List<Way> CHOOSING = wide(
            ways("while count(front, {t}) > 0 (send {t} from front to hands)", "while that chest has {t}, take it", "take the {t} until that chest has none"),
            ways("until inventory_empty(front) (send from front to network)", "keep emptying that chest into the network", "until that chest is empty, move it all"),
            ways("if count(front, {t}) > {n} (send {t} from front to hands)", "if that chest has over {n} {t}, take them", "take the {t} if there are over {n}"),
            ways("if count(hands, {t}) = 0 (fetch {t})", "if you hold no {t}, fetch some", "fetch {t} if you have none"),
            ways("if night (home) else (farm)", "go home at night, otherwise farm", "if it is night go home, else farm"),
            ways("if inventory_full(front) (send from front to network)", "if that chest is full, empty it", "empty that chest if it is full"),
            ways("wait until inventory_empty(front), come", "wait until that chest is empty, then come", "come when that chest is empty"),
            ways("wait until redstone(mark({a})) > 0, go {a}", "when mark {a} gets a signal, go there", "go to mark {a} once it has power"),
            ways("repeat {n} (send {t} from {a} to {b}, wait 1)", "move {t} from {a} to {b} {n} times, slowly"),
            ways("repeat {n} (dig at front, forward 1)", "tunnel {n} blocks ahead", "dig a tunnel {n} blocks long"),
            ways("repeat (send from front to network, wait 5)", "keep that chest empty", "keep moving that chest to the network"),
            ways("set $n count(front, {t}), say $n", "count the {t} in that chest", "tell me how many {t} that chest has"),
            ways("set $n network_count({t}), say $n", "count the {t} on the network", "how many {t} does the network have"),
            ways("set @home_chest here, say done", "remember this place", "remember where you are"));

    private static final List<Way> LISTENING = wide(
            ways("on redstone at {a} (go {a})", "whenever mark {a} gets a signal, go there", "go to mark {a} whenever it gets power"),
            ways("on timer {n} (say {w})", "every {n} seconds say {w}", "say {w} every {n} seconds"),
            ways("on item {t} at front (send {t} from front to network)", "when {t} arrives, put it on the network", "whenever {t} comes in, move it to the network"),
            ways("on block at {a} (come)", "when mark {a} changes, come to me", "come to me if mark {a} changes"),
            ways("when count(front, {t}) > {n} (say {w})", "say {w} once that chest has {n} {t}"));

    /** What the full language lets people say, a kind at a time. */
    private static final List<List<Way>> FULL = List.of(MOVING, ACTING, AREAS, FILTERING, EACH, OWN_STEPS, CHOOSING, LISTENING);

    @SafeVarargs
    private static List<Way> wide(List<Way>... groups) {
        return Stream.of(groups).flatMap(List::stream).toList();
    }

    /** Orders that check or wait before they act. */
    private static final List<Way> RIPE = List.of(
            new Way("if the crops are ripe, harvest them", "if ripe (harvest)"),
            new Way("harvest if anything is ripe", "if ripe (harvest)"),
            new Way("if anything is ripe, harvest it", "if ripe (harvest)"),
            new Way("if the wheat is ready, harvest it", "if ripe (harvest)"),
            new Way("harvest the crops if they are ripe", "if ripe (harvest)"),
            new Way("when the crops are ripe, harvest them", "wait until ripe, harvest"),
            new Way("harvest when the wheat is ready", "wait until ripe, harvest"),
            new Way("wait for the crops, then harvest", "wait until ripe, harvest"),
            new Way("keep harvesting", "repeat (harvest)"),
            new Way("harvest all day", "repeat (harvest)"),
            new Way("keep picking the crops", "repeat (harvest)"),
            new Way("harvest until your hands are full", "until full (harvest)"),
            new Way("harvest till you are full", "until full (harvest)"));
    private static final List<Way> NIGHT = List.of(
            new Way("when it gets dark, go home", "wait until night, home"),
            new Way("go home at night", "wait until night, home"),
            new Way("come home when night falls", "wait until night, home"),
            new Way("at nightfall go home", "wait until night, home"),
            new Way("when night comes, go home", "wait until night, home"),
            new Way("go home when it is dark", "wait until night, home"),
            new Way("come to me at night", "wait until night, come"),
            new Way("when it gets dark, come to me", "wait until night, come"),
            new Way("find me when it is dark", "wait until night, come"));
    private static final List<Way> PICKING = List.of(
            new Way("keep picking things up", "repeat (pickup)"),
            new Way("keep collecting the drops", "repeat (pickup)"),
            new Way("pick things up until you are full", "until full (pickup)"),
            new Way("collect drops till your hands are full", "until full (pickup)"));
    private static final List<String> IF_STOCKED = List.of("bring me %s if there is any", "if we have %s, bring me some",
            "if there is %s, fetch it", "fetch %s if we have any", "if there is any %s, bring it",
            "get me %s if we have some");

    private static final List<String> NAMES = List.of("pip", "clank", "moss", "lantern", "bolt", "tinker", "wick",
            "pebble", "copper", "agent");
    private static final List<String> BEFORE = List.of("", "", "", "", "please ", "now ", "ok ", "hey ", "%s, ",
            "%s ", "right, ", "and ", "quick, ", "listen, ");
    private static final List<String> AFTER = List.of("", "", "", "", " please", " now", " thanks", " thank you",
            ", please", " quickly", " for me", "!", ".", " right now", " when you can");
    /** Things to fetch and put away. Every fifth one never appears in practice, only in the test. */
    static final List<String> THINGS = List.of(
            "bread", "wheat", "wheat seeds", "seeds", "carrots", "potatoes", "baked potatoes", "beetroots",
            "beetroot seeds", "apples", "golden apples", "melon slices", "pumpkins", "pumpkin pie", "sugar",
            "sugar cane", "eggs", "milk", "cake", "cookies", "honey", "honeycomb", "sweet berries", "mushrooms",
            "mushroom stew", "rabbit stew", "cod", "salmon", "cooked cod", "cooked salmon", "beef", "steak",
            "porkchops", "chicken", "mutton", "kelp", "dried kelp", "coal", "charcoal", "iron ingots",
            "iron nuggets", "gold ingots", "gold nuggets", "copper ingots", "raw iron", "raw copper", "raw gold",
            "diamonds", "emeralds", "redstone", "lapis", "quartz", "amethyst shards", "flint", "clay", "clay balls",
            "bricks", "glass", "glass panes", "sand", "red sand", "gravel", "dirt", "cobblestone", "stone",
            "deepslate", "cobbled deepslate", "andesite", "granite", "diorite", "oak logs", "birch logs",
            "spruce logs", "jungle logs", "acacia logs", "dark oak logs", "oak planks", "spruce planks",
            "birch planks", "sticks", "torches", "ladders", "chests", "barrels", "rails", "buckets",
            "water buckets", "shears", "pickaxe", "axe", "shovel", "hoe", "sword", "bow", "arrows", "string", "wool",
            "white wool", "leather", "feathers", "bones", "bone meal", "paper", "books", "ink sacs", "slime balls",
            "gunpowder", "snowballs", "ice", "packed ice", "lanterns", "candles", "saddles", "leads", "name tags",
            "flowers", "dandelions", "poppies", "oak saplings", "cactus", "vines", "lily pads");
    /** Things to plant, held out the same way. */
    static final List<String> SEEDS = List.of("wheat seeds", "seeds", "carrots", "potatoes", "beetroot seeds",
            "melon seeds", "pumpkin seeds", "torchflower seeds", "wheat", "potato");
    /** Blocks to mine, held out the same way. */
    static final List<String> BLOCKS = List.of("stone", "cobblestone", "dirt", "sand", "gravel", "clay", "coal ore",
            "iron ore", "copper ore", "gold ore", "deepslate", "andesite", "granite", "diorite", "oak logs",
            "birch logs", "spruce logs", "logs", "netherrack", "snow", "ice", "glass", "terracotta", "sandstone",
            "obsidian");
    /** Words to say, held out the same way. */
    static final List<String> WORDS = List.of("hello", "good morning", "dinner is ready", "all done", "hi there",
            "on my way", "time to eat", "welcome home", "well done", "nice work", "good night", "the wheat is in",
            "help", "hurry up", "over here", "back soon", "look out", "thank you", "watch this", "happy birthday");
    /** Creatures to fight and to tend, tools to use, tags and kinds, held out the same way. */
    static final List<String> MOBS = List.of("zombies", "skeletons", "creepers", "spiders", "husks", "drowned", "witches", "pillagers", "slimes",
            "phantoms", "strays", "vindicators", "endermen", "blazes", "ghasts");
    static final List<String> ANIMALS = List.of("sheep", "cows", "pigs", "chickens", "rabbits", "goats", "llamas", "horses", "foxes", "bees",
            "mooshrooms", "camels", "turtles", "cats", "wolves");
    static final List<String> FLUIDS = List.of("water", "lava", "milk", "honey", "oil", "tar", "brine", "resin", "ink", "mud");
    static final List<String> TOOLS = List.of("hoe", "shears", "bucket", "shovel", "bone meal", "brush", "axe", "pickaxe", "water bucket", "fishing rod",
            "iron hoe", "stone axe", "lever", "compass", "spyglass");
    static final List<String> TAGS = List.of("logs", "planks", "wool", "saplings", "leaves", "flowers", "coals", "stairs", "slabs", "doors",
            "fences", "beds", "banners", "boats", "candles", "carpets", "signs", "rails", "arrows", "anvil");
    /** Kinds a filter model knows, all of them in practice. */
    private static final List<String> KINDS = List.of("food", "plants", "wood", "stone", "ores", "tools");

    /** Every fifth name of each list, held out of practice wherever else it appears. */
    private static final Set<String> HELD_OUT = Stream.of(THINGS, SEEDS, BLOCKS, WORDS, MOBS, ANIMALS, FLUIDS, TOOLS, TAGS)
            .flatMap(names -> IntStream.range(0, names.size()).filter(i -> i % 5 == 4).mapToObj(names::get))
            .collect(Collectors.toUnmodifiableSet());
    private static final String[] NUMBERS = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight",
            "nine", "ten", "eleven", "twelve"};

    private static final String[] SYLLABLES = {"ba", "be", "bo", "da", "di", "do", "fa", "fi", "ga", "go", "ka", "ki",
            "ko", "la", "li", "lo", "ma", "mi", "mo", "na", "ni", "no", "pa", "pi", "po", "ra", "ri", "ro", "sa", "si",
            "so", "ta", "ti", "to", "va", "vi", "wa", "wi", "za", "zo"};

    private Orders() {
    }

    /**
     * One order in a random wording, short enough to be shown whole, and the program it means. {@code test} draws
     * things, seeds, blocks and words from the ones practice never shows; practice also asks for made-up ones, so a
     * model learns to copy any name it hears.
     */
    public static Example example(Random rng, boolean test) {
        while (true) {
            Way core = core(rng, test);
            String said = BEFORE.get(rng.nextInt(BEFORE.size())).formatted(NAMES.get(rng.nextInt(NAMES.size())))
                    + core.said() + AFTER.get(rng.nextInt(AFTER.size()));
            if (said.length() <= LONGEST && core.meant().length() <= WRITTEN) {
                return new Example(said, core.meant());
            }
        }
    }

    /** What is said and meant, before anything is put round it. */
    private static Way core(Random rng, boolean test) {
        if (rng.nextFloat() < FULL_SHARE) {
            return full(rng, test);
        }
        float r = rng.nextFloat();
        if (r < 0.62f) {
            Step s = rng.nextFloat() < 0.3f ? (rng.nextBoolean() ? FETCH : FETCH_SOME) : SINGLE.get(rng.nextInt(SINGLE.size()));
            return fill(rng, s, test);
        }
        if (r < 0.82f) {
            Step a = FINITE.get(rng.nextInt(FINITE.size()));
            Step b = FINITE.get(rng.nextInt(FINITE.size()));
            Way first = fill(rng, a, test), second = fill(rng, b, test);
            return new Way(THEN.get(rng.nextInt(THEN.size())).formatted(first.said(), second.said()), first.meant() + ", " + second.meant());
        }
        return switch (rng.nextInt(4)) {
            case 0 -> RIPE.get(rng.nextInt(RIPE.size()));
            case 1 -> NIGHT.get(rng.nextInt(NIGHT.size()));
            case 2 -> PICKING.get(rng.nextInt(PICKING.size()));
            default -> {
                String thing = name(rng, THINGS, test);
                yield new Way(IF_STOCKED.get(rng.nextInt(IF_STOCKED.size())).formatted(thing),
                        "if stocked %s (fetch %s)".formatted(thing, thing));
            }
        };
    }

    /** An order in the full language, now and then with a first thing to do after it. */
    private static Way full(Random rng, boolean test) {
        List<Way> kind = FULL.get(rng.nextInt(FULL.size()));
        Way w = filled(rng, kind.get(rng.nextInt(kind.size())), test);
        if (rng.nextFloat() < 0.15f && kind != LISTENING && kind != OWN_STEPS) {
            Way then = fill(rng, FINITE.get(rng.nextInt(FINITE.size())), test);
            return new Way(THEN.get(rng.nextInt(THEN.size())).formatted(w.said(), then.said()), w.meant() + ", " + then.meant());
        }
        return w;
    }

    /** A way of saying something in the full language, its blanks filled alike in what is said and what is meant. */
    private static Way filled(Random rng, Way w, boolean test) {
        String said = w.said(), meant = w.meant();
        int n = 2 + rng.nextInt(rng.nextBoolean() ? 11 : 63);
        int a = 1 + rng.nextInt(4), b = a + 1 + rng.nextInt(4);
        String thing = name(rng, THINGS, test);
        String other = name(rng, THINGS, test);
        String[][] blanks = {
            {"{n}", n < NUMBERS.length && rng.nextBoolean() ? NUMBERS[n] : String.valueOf(n), String.valueOf(n)},
            {"{a}", a < NUMBERS.length && rng.nextBoolean() ? NUMBERS[a] : String.valueOf(a), String.valueOf(a)},
            {"{b}", b < NUMBERS.length && rng.nextBoolean() ? NUMBERS[b] : String.valueOf(b), String.valueOf(b)},
            {"{t}", thing, thing},
            {"{u}", other, other},
        };
        for (String[] blank : blanks) {
            said = said.replace(blank[0], blank[1]);
            meant = meant.replace(blank[0], blank[2]);
        }
        String[][] names = {
            {"{s}", said.contains("{s}") ? name(rng, BLOCKS, test) : ""},
            {"{d}", said.contains("{d}") ? name(rng, SEEDS, test) : ""},
            {"{m}", said.contains("{m}") ? name(rng, MOBS, test) : ""},
            {"{l}", said.contains("{l}") ? name(rng, ANIMALS, test) : ""},
            {"{f}", said.contains("{f}") ? name(rng, FLUIDS, test) : ""},
            {"{o}", said.contains("{o}") ? name(rng, TOOLS, test) : ""},
            {"{g}", said.contains("{g}") ? name(rng, TAGS, test) : ""},
            {"{k}", said.contains("{k}") ? KINDS.get(rng.nextInt(KINDS.size())) : ""},
            {"{w}", said.contains("{w}") ? name(rng, WORDS, test) : ""},
            {"{c}", said.contains("{c}") ? madeUpStep(rng) : ""},
        };
        for (String[] blank : names) {
            said = said.replace(blank[0], blank[1]);
            meant = meant.replace(blank[0], blank[1]);
        }
        return new Way(said, meant);
    }

    /** One way of saying {@code s}, its blanks filled. */
    private static Way fill(Random rng, Step s, boolean test) {
        Way w = s.ways().get(rng.nextInt(s.ways().size()));
        String word = switch (s.arg()) {
            case THING, COUNT -> name(rng, THINGS, test);
            case SEED -> name(rng, SEEDS, test);
            case BLOCK -> name(rng, BLOCKS, test);
            case WORDS -> name(rng, WORDS, test);
            default -> "";
        };
        int n = switch (s.arg()) {
            case SECONDS -> 1 + rng.nextInt(rng.nextBoolean() ? 12 : 60);
            case MARK -> 1 + rng.nextInt(8);
            case COUNT -> 2 + rng.nextInt(rng.nextBoolean() ? 11 : 63);
            default -> 0;
        };
        // small numbers are as often said in words as in digits
        String said = n > 0 && n < NUMBERS.length && rng.nextBoolean() ? NUMBERS[n] : String.valueOf(n);
        return new Way(w.said().formatted(word, said), w.meant().formatted(word, n));
    }

    /** A name from {@code names}, a held-out one for the test; practice also makes names up. */
    private static String name(Random rng, List<String> names, boolean test) {
        if (!test && rng.nextFloat() < 0.4f) {
            return madeUp(rng);
        }
        while (true) {
            String n = names.get(rng.nextInt(names.size()));
            if (HELD_OUT.contains(n) == test) {
                return n;
            }
        }
    }

    /** A word that names nothing, like "kodari" or "mipo sanis". */
    static String madeUp(Random rng) {
        StringBuilder sb = new StringBuilder();
        int words = rng.nextFloat() < 0.25f ? 2 : 1;
        for (int w = 0; w < words; w++) {
            if (w > 0) {
                sb.append(' ');
            }
            int n = 2 + rng.nextInt(2);
            for (int i = 0; i < n; i++) {
                sb.append(SYLLABLES[rng.nextInt(SYLLABLES.length)]);
            }
            if (rng.nextFloat() < 0.3f) {
                sb.append(rng.nextBoolean() ? 's' : 'n');
            }
        }
        return sb.toString();
    }

    /** A name for a step of its own, one word, like "kodari". */
    private static String madeUpStep(Random rng) {
        StringBuilder sb = new StringBuilder();
        for (int i = 2 + rng.nextInt(2); i > 0; i--) {
            sb.append(SYLLABLES[rng.nextInt(SYLLABLES.length)]);
        }
        return sb.toString();
    }

    /**
     * What a window shows of an order and its program, and the letters of it that are scored: those a model writing the
     * program would write while reading just this much.
     */
    public record Window(String text, int scoredFrom, int scoredTo) {
    }

    /** The windows a model reads as it writes an order's program, in turn; every letter is scored in exactly one. */
    public static List<Window> windows(Example ex) {
        String prompt = prompt(ex.said()), written = ex.meant() + "\n";
        int room = Vocab.CONTEXT - prompt.length(), stride = Sampler.stride(prompt.length());
        List<Window> out = new ArrayList<>();
        for (int from = 0; ; from += stride) {
            int end = Math.min(written.length(), from + room + 1);
            String text = prompt + written.substring(from, end);
            out.add(new Window(text, prompt.length() + (from == 0 ? 0 : room - stride + 1), text.length()));
            if (end == written.length()) {
                return out;
            }
        }
    }

    /**
     * Its loss and score on orders it has never seen: the share of programs it would write whole. Greedy writing goes wrong at
     * the first letter that is not the likeliest, so checking each letter in the window it would be written from says exactly
     * what it would write, for a tenth of the work of writing them all out; and what it would write, if it is right, is what
     * {@link io.github.metrovoc.residual.agent.Program#parse} reads back and {@code toString} writes.
     */
    static double[] measure(CompiledGraph model) {
        Random rng = new Random(12345);
        List<Window> windows = new ArrayList<>();
        List<Integer> owner = new ArrayList<>();
        for (int i = 0; i < TESTED; i++) {
            for (Window w : windows(example(rng, true))) {
                windows.add(w);
                owner.add(i);
            }
        }
        boolean[] wrong = new boolean[TESTED];
        double loss = 0;
        long letters = 0;
        int t = Vocab.CONTEXT, batch = 64;
        for (int start = 0; start < windows.size(); start += batch) {
            int size = Math.min(batch, windows.size() - start);
            int[] ids = new int[size * t];
            Arrays.fill(ids, Vocab.WORLD.id('\n'));
            for (int b = 0; b < size; b++) {
                int[] spelled = Vocab.WORLD.spell(windows.get(start + b).text());
                System.arraycopy(spelled, 0, ids, b * t, Math.min(t, spelled.length));
            }
            Tensor logits = model.forward(null, new Tokens(ids, size, t), null);
            int v = logits.dim(-1);
            for (int b = 0; b < size; b++) {
                Window w = windows.get(start + b);
                int[] spelled = Vocab.WORLD.spell(w.text());
                for (int i = w.scoredFrom(); i < w.scoredTo(); i++) {
                    int off = (b * t + i - 1) * v;
                    int best = Trainer.argmax(logits.data, off, v);
                    wrong[owner.get(start + b)] |= best != spelled[i];
                    double sum = 0;
                    for (int k = 0; k < v; k++) {
                        sum += Math.exp(logits.data[off + k] - logits.data[off + best]);
                    }
                    loss += Math.log(sum) - (logits.data[off + spelled[i]] - logits.data[off + best]);
                    letters++;
                }
            }
        }
        int right = 0;
        for (boolean w : wrong) {
            right += w ? 0 : 1;
        }
        return new double[]{letters == 0 ? 0 : loss / letters, (double) right / TESTED};
    }

    /** What a model is shown for an order, in the format it practised. */
    public static String prompt(String said) {
        return "> " + clean(said) + "\n= ";
    }

    /** Lower case, only characters models know, single spaces, at most {@link #LONGEST} characters. */
    public static String clean(String said) {
        StringBuilder sb = new StringBuilder();
        for (char c : said.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isWhitespace(c)) {
                c = ' ';
            }
            if (Vocab.WORLD.reads(c) && c != '\n' && c != '>' && c != '=' && c != '|' && !(c == ' ' && (sb.isEmpty()
                    || sb.charAt(sb.length() - 1) == ' '))) {
                sb.append(c);
            }
        }
        String s = sb.toString().strip();
        return s.length() > LONGEST ? s.substring(0, LONGEST).strip() : s;
    }
}
