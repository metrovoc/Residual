package io.github.metrovoc.residual.agent;

import io.github.metrovoc.residual.learn.Kinds;
import io.github.metrovoc.residual.model.ModelLink;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jetbrains.annotations.Nullable;

/** The filters of the language: things by name, tag, mod or pattern, their lists white and black, and what a filter model says. */
final class Picks {
    /** Lets everything through. */
    static final Picker ALL = new Picker() {
        @Override
        public boolean exactly(ItemStack s) {
            return !s.isEmpty();
        }

        @Override
        public boolean passes(FluidResource fluid) {
            return !fluid.isEmpty();
        }

        @Override
        public boolean passes(Entity e) {
            return true;
        }

        @Override
        public String thing() {
            return "everything";
        }
    };

    private Picks() {
    }

    /** What {@code any or any except but where where} lets through, worked out now. */
    static Picker of(Eval e, Expr.Pick pick) {
        if (pick.all()) {
            return ALL;
        }
        if (pick.but().isEmpty() && pick.where() == null && pick.any().size() == 1) {
            return of(e, e.eval(pick.any().getFirst()));
        }
        List<Picker> any = new ArrayList<>();
        for (Expr x : pick.any()) {
            any.add(of(e, e.eval(x)));
        }
        List<Picker> but = new ArrayList<>();
        for (Expr x : pick.but()) {
            but.add(of(e, e.eval(x)));
        }
        Expr where = pick.where();
        String shown = pick.text();
        return new Picker() {
            private boolean some(List<Picker> in, Predicate<Picker> test) {
                for (Picker p : in) {
                    if (test.test(p)) {
                        return true;
                    }
                }
                return false;
            }

            private boolean check(Value it) {
                return where == null || e.with("$it", it, where).truth();
            }

            @Override
            public boolean exactly(ItemStack s) {
                return !s.isEmpty() && (any.isEmpty() || some(any, p -> p.passes(s))) && !some(but, p -> p.passes(s)) && check(new Value.Stack(s));
            }

            @Override
            public boolean passes(FluidResource f) {
                return !f.isEmpty() && (any.isEmpty() || some(any, p -> p.passes(f))) && !some(but, p -> p.passes(f)) && check(new Value.Liquid(f, 0));
            }

            @Override
            public boolean passes(Entity x) {
                return (any.isEmpty() || some(any, p -> p.passes(x))) && !some(but, p -> p.passes(x)) && check(new Value.Creature(x));
            }

            @Override
            public String thing() {
                return shown;
            }
        };
    }

    /** What a value lets through: text names things, an item its own kind, a list any of what it holds. */
    static Picker of(Eval e, Value v) {
        return switch (v) {
            case Value.Sieve s -> s.picker();
            case Value.Text t -> Things.of(t.v());
            case Value.Stack s -> same(s.stack());
            case Value.Solid s -> same(new ItemStack(s.state().getBlock().asItem()));
            case Value.Liquid l -> new Picker() {
                @Override
                public boolean exactly(ItemStack x) {
                    return false;
                }

                @Override
                public boolean passes(FluidResource f) {
                    return f.getFluid().isSame(l.fluid().getFluid());
                }

                @Override
                public String thing() {
                    return l.show();
                }
            };
            case Value.Creature c -> new Picker() {
                @Override
                public boolean exactly(ItemStack x) {
                    return false;
                }

                @Override
                public boolean passes(Entity x) {
                    return x.getType() == c.entity().getType();
                }

                @Override
                public String thing() {
                    return c.show();
                }
            };
            case Value.Many m -> {
                List<Picker> any = new ArrayList<>(m.items().size());
                for (Value x : m.items()) {
                    any.add(of(e, x));
                }
                yield anyOf(any, v.show());
            }
            case Value.Nothing n -> ALL;
            default -> Things.of(v.show());
        };
    }

    static Picker anyOf(List<Picker> any, String shown) {
        return new Picker() {
            @Override
            public boolean exactly(ItemStack s) {
                for (Picker p : any) {
                    if (p.passes(s)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean passes(FluidResource f) {
                for (Picker p : any) {
                    if (p.passes(f)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean passes(Entity x) {
                for (Picker p : any) {
                    if (p.passes(x)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public String thing() {
                return shown;
            }
        };
    }

    /** A black list: whatever does not pass. */
    static Picker not(Picker no) {
        return new Picker() {
            @Override
            public boolean exactly(ItemStack s) {
                return !s.isEmpty() && !no.passes(s);
            }

            @Override
            public boolean passes(FluidResource f) {
                return !f.isEmpty() && !no.passes(f);
            }

            @Override
            public boolean passes(Entity x) {
                return !no.passes(x);
            }

            @Override
            public String thing() {
                return "other_than(" + no.thing() + ")";
            }
        };
    }

    /** This kind of item, with these components. */
    static Picker same(ItemStack kind) {
        return new Picker() {
            @Override
            public boolean exactly(ItemStack s) {
                return ItemStack.isSameItemSameComponents(s, kind);
            }

            @Override
            public String thing() {
                return Things.name(kind);
            }
        };
    }

    /** {@code tag(logs)}: what carries a tag. A tag without a namespace is looked for under both minecraft and c. */
    static Picker tag(String name) {
        String path = name.strip().replace(' ', '_');
        List<TagKey<Item>> items = new ArrayList<>();
        List<TagKey<Fluid>> fluids = new ArrayList<>();
        List<TagKey<EntityType<?>>> entities = new ArrayList<>();
        for (String ns : path.contains(":") ? List.of("") : List.of("minecraft:", "c:")) {
            Identifier id = Identifier.tryParse(ns + path);
            if (id != null) {
                items.add(TagKey.create(Registries.ITEM, id));
                fluids.add(TagKey.create(Registries.FLUID, id));
                entities.add(TagKey.create(Registries.ENTITY_TYPE, id));
            }
        }
        return new Picker() {
            @Override
            public boolean exactly(ItemStack s) {
                for (TagKey<Item> t : items) {
                    if (s.is(t)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean passes(FluidResource f) {
                for (TagKey<Fluid> t : fluids) {
                    if (f.getFluid().defaultFluidState().is(t)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean passes(Entity e) {
                for (TagKey<EntityType<?>> t : entities) {
                    if (e.is(t)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public String thing() {
                return "tag(" + name + ")";
            }
        };
    }

    /** {@code mod(residual)}: what a mod added. */
    static Picker mod(String name) {
        String ns = name.strip().replace(' ', '_');
        return new Picker() {
            @Override
            public boolean exactly(ItemStack s) {
                return BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace().equals(ns);
            }

            @Override
            public boolean passes(FluidResource f) {
                return BuiltInRegistries.FLUID.getKey(f.getFluid()).getNamespace().equals(ns);
            }

            @Override
            public boolean passes(Entity e) {
                return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals(ns);
            }

            @Override
            public String thing() {
                return "mod(" + name + ")";
            }
        };
    }

    /** {@code regex("_ore$")}: what has a name the pattern finds something in; a pattern that cannot be read lets nothing through. */
    static Picker regex(String pattern) {
        Pattern p;
        try {
            p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException x) {
            p = null;
        }
        Pattern compiled = p;
        return new Picker() {
            @Override
            public boolean exactly(ItemStack s) {
                return compiled != null && compiled.matcher(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath()).find();
            }

            @Override
            public boolean passes(FluidResource f) {
                return compiled != null && compiled.matcher(BuiltInRegistries.FLUID.getKey(f.getFluid()).getPath()).find();
            }

            @Override
            public boolean passes(Entity e) {
                return compiled != null && compiled.matcher(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath()).find();
            }

            @Override
            public String thing() {
                return "regex(\"" + pattern + "\")";
            }
        };
    }

    /** What each body's filter model said of each thing, until the model changes. */
    private static final Map<Body, Judged> JUDGED = new WeakHashMap<>();

    private static final class Judged {
        ItemStack model = ItemStack.EMPTY;
        final Map<Item, Kinds.Kind> kinds = new HashMap<>();
        @Nullable
        Item asking;
    }

    /**
     * {@code kind(food)}: the learned filter, what a body's filter model is sure is of a kind. It asks about one thing at a
     * time and nothing passes until the answer comes; an answer under {@link Kinds#SURE}, or no model at all, counts as other.
     */
    static Picker kind(Body a, String name) {
        Kinds.Kind kind = Kinds.Kind.labelled(name);
        return new Picker() {
            @Override
            public boolean exactly(ItemStack s) {
                Kinds.Kind said = judge(a, s.getItem());
                return said != null && said == (kind == null ? Kinds.Kind.OTHER : kind);
            }

            @Override
            public String thing() {
                return "kind(" + name + ")";
            }
        };
    }

    @Nullable
    private static Kinds.Kind judge(Body a, Item item) {
        Judged j = JUDGED.computeIfAbsent(a, b -> new Judged());
        ItemStack model = a.filterModel();
        if (!ItemStack.isSameItemSameComponents(model, j.model)) {
            j.model = model.copy();
            j.kinds.clear();
            j.asking = null;
        }
        Kinds.Kind known = j.kinds.get(item);
        if (known != null) {
            return known;
        }
        ModelLink link = ModelLink.of(a.world(), a.place(), model);
        if (link instanceof ModelLink.Missing) {
            return Kinds.Kind.OTHER;
        }
        if (j.asking == null) {
            j.asking = item;
            link.ask(a.world(), Kinds.prompt(Things.name(new ItemStack(item))), 1, '\n', answer -> {
                j.asking = null;
                Kinds.Kind said = answer == null || answer.text().isEmpty() ? null : Kinds.Kind.of(answer.text().charAt(0));
                j.kinds.put(item, said != null && answer.confidence() >= Kinds.SURE ? said : Kinds.Kind.OTHER);
            });
        }
        return null;
    }
}
