//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package ch.njol.skript;

import ch.njol.skript.aliases.Aliases;
import ch.njol.skript.aliases.ItemType;
import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.command.CommandEvent;
import ch.njol.skript.command.Commands;
import ch.njol.skript.command.ScriptCommand;
import ch.njol.skript.config.Config;
import ch.njol.skript.config.EntryNode;
import ch.njol.skript.config.Node;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.config.SimpleNode;
import ch.njol.skript.effects.Delay;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Conditional;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.Loop;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.lang.SelfRegisteringSkriptEvent;
import ch.njol.skript.lang.SkriptEvent;
import ch.njol.skript.lang.SkriptEventInfo;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.Statement;
import ch.njol.skript.lang.Trigger;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.lang.TriggerSection;
import ch.njol.skript.lang.While;
import ch.njol.skript.lang.function.Function;
import ch.njol.skript.lang.function.FunctionEvent;
import ch.njol.skript.lang.function.Functions;
import ch.njol.skript.localization.Language;
import ch.njol.skript.localization.Message;
import ch.njol.skript.localization.PluralizingArgsMessage;
import ch.njol.skript.log.CountingLogHandler;
import ch.njol.skript.log.ErrorDescLogHandler;
import ch.njol.skript.log.ParseLogHandler;
import ch.njol.skript.log.RetainingLogHandler;
import ch.njol.skript.log.SkriptLogger;
import ch.njol.skript.registrations.Classes;
import ch.njol.skript.registrations.Converters;
import ch.njol.skript.util.Date;
import ch.njol.skript.util.ExceptionUtils;
import ch.njol.skript.variables.Variables;
import ch.njol.util.Callback;
import ch.njol.util.Kleenean;
import ch.njol.util.NonNullPair;
import ch.njol.util.StringUtils;
import ch.njol.util.coll.CollectionUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

import org.bukkit.event.Event;
import org.eclipse.jdt.annotation.Nullable;

public final class ScriptLoader {
    private static final Message m_no_errors = new Message("skript.no errors");
    private static final Message m_no_scripts = new Message("skript.no scripts");
    private static final PluralizingArgsMessage m_scripts_loaded = new PluralizingArgsMessage("skript.scripts loaded");
    @Nullable
    public static Config currentScript = null;
    @Nullable
    private static String currentEventName = null;
    @Nullable
    private static Class<? extends Event>[] currentEvents = null;
    public static List<TriggerSection> currentSections = new ArrayList<>();
    public static List<Loop> currentLoops = new ArrayList<>();
    private static final Map<String, ItemType> currentAliases = new HashMap<>();
    static final HashMap<String, String> currentOptions = new HashMap<>();
    private static final ScriptInfo loadedScripts = new ScriptInfo();
    public static Kleenean hasDelayBefore;
    private static String indentation;
    private static final FileFilter scriptFilter;

    private ScriptLoader() {
    }

    @Nullable
    public static String getCurrentEventName() {
        return currentEventName;
    }

    @SafeVarargs
    public static void setCurrentEvent(String name, @Nullable Class<? extends Event>... events) {
        currentEventName = name;
        currentEvents = events;
        hasDelayBefore = Kleenean.FALSE;
    }

    public static void deleteCurrentEvent() {
        currentEventName = null;
        currentEvents = null;
        hasDelayBefore = Kleenean.FALSE;
    }

    public static Map<String, ItemType> getScriptAliases() {
        return currentAliases;
    }

    static final ScriptInfo loadScripts() {
        File scriptsFolder = new File(Skript.getInstance().getDataFolder(), "scripts" + File.separator);
        if (!scriptsFolder.isDirectory()) {
            scriptsFolder.mkdirs();
        }

        Date start = new Date();
        ErrorDescLogHandler h = (ErrorDescLogHandler) SkriptLogger.startLogHandler(new ErrorDescLogHandler((String) null, (String) null, m_no_errors.toString()));

        ScriptInfo i;
        try {
            Language.setUseLocal(false);
            i = loadScripts(scriptsFolder);
            synchronized (loadedScripts) {
                loadedScripts.add(i);
            }
        } finally {
            Language.setUseLocal(true);
            h.stop();
        }

        if (i.files == 0) {
            Skript.warning(m_no_scripts.toString());
        }

        if (Skript.logNormal() && i.files > 0) {
            Skript.info(m_scripts_loaded.toString(new Object[]{i.files, i.triggers, i.commands, start.difference(new Date())}));
        }

        SkriptEventHandler.registerBukkitEvents();
        return i;
    }

    public final static ScriptInfo loadScripts(File directory) {
        ScriptInfo i = new ScriptInfo();
        boolean wasLocal = Language.setUseLocal(false);

        try {
            File[] files = directory.listFiles(scriptFilter);
            assert files != null;
            Arrays.sort(files);

            for (File f : files) {
                if (f.isDirectory()) {
                    i.add(loadScripts(f));
                } else {
                    i.add(loadScript(f));
                }
            }
        } finally {
            if (wasLocal) {
                Language.setUseLocal(true);
            }

        }

        return i;
    }

    public final static ScriptInfo loadScripts(File[] files) {
        Arrays.sort(files);
        ScriptInfo i = new ScriptInfo();
        boolean wasLocal = Language.setUseLocal(false);

        try {
            for (File f : files) {
                assert f != null : Arrays.toString(files);

                i.add(loadScript(f));
            }
        } finally {
            if (wasLocal) {
                Language.setUseLocal(true);
            }

        }

        synchronized (loadedScripts) {
            loadedScripts.add(i);
        }

        SkriptEventHandler.registerBukkitEvents();
        return i;
    }

    private final static ScriptInfo loadScript(final File f) {
        try {
            final Config config = new Config(f, true, false, ":");
            if (SkriptConfig.keepConfigsLoaded.value())
                SkriptConfig.configs.add(config);

            int numTriggers = 0;
            int numCommands = 0;
            int numFunctions = 0;

            currentAliases.clear();
            currentOptions.clear();
            currentScript = config;

            final CountingLogHandler numErrors = SkriptLogger.startLogHandler(new CountingLogHandler(SkriptLogger.SEVERE));

            try {
                final List<SectionNode> functionNodes = new ArrayList<>();
                final List<SectionNode> triggerNodes = new ArrayList<>();
                final List<SectionNode> commandNodes = new ArrayList<>();
                final List<SectionNode> aliasesNodes = new ArrayList<>();
                final List<SectionNode> optionsNode = new ArrayList<>();
                final List<SectionNode> variablesNodes = new ArrayList<>();

                for (final Node cnode : config.getMainNode()) {
                    if (!(cnode instanceof SectionNode)) {
                        Skript.error("invalid line - all code has to be put into triggers");
                        continue;
                    }
                    final SectionNode node = ((SectionNode) cnode);
                    String event = node.getKey();

                    if (event == null)
                        continue;

                    final String lowered = event.toLowerCase();

                    if (lowered.startsWith("function ")) {
                        functionNodes.add(node);
                        continue;
                    } else if (lowered.startsWith("command ")) {
                        commandNodes.add(node);
                        continue;
                    } else if (lowered.startsWith("aliases")) {
                        aliasesNodes.add(node);
                        continue;
                    } else if (lowered.startsWith("options")) {
                        optionsNode.add(node);
                        continue;
                    } else if (lowered.startsWith("variables")) {
                        variablesNodes.add(node);
                        continue;
                    } else {
                        triggerNodes.add(node);
                        continue;
                    }
                }

                // Carregar Aliases
                for (SectionNode node : aliasesNodes) {
                    node.convertToEntries(0, "=");
                    for (final Node n : node) {
                        if (!(n instanceof EntryNode)) {
                            Skript.error("invalid line in aliases section");
                            continue;
                        }
                        final ItemType t = Aliases.parseAlias(((EntryNode) n).getValue());
                        if (t == null)
                            continue;
                        currentAliases.put(((EntryNode) n).getKey().toLowerCase(), t);
                    }
                }

                // Carregar todos options
                for (SectionNode node : optionsNode) {
                    node.convertToEntries(0);
                    for (final Node n : node) {
                        if (!(n instanceof EntryNode)) {
                            Skript.error("invalid line in options");
                            continue;
                        }
                        currentOptions.put(((EntryNode) n).getKey(), ((EntryNode) n).getValue());
                    }
                }

                // Carregar todas variaveis
                for (SectionNode node : variablesNodes) {
                    // TODO allow to make these override existing variables

                    node.convertToEntries(0, "=");
                    for(final Node n : node) {
                        if (!(n instanceof EntryNode)) {
                            Skript.error("Invalid line in variables section");
                            continue; } String name = ((EntryNode) n).getKey().toLowerCase(Locale.ENGLISH);
                        if(name.startsWith("{") && name.endsWith("}")) name = "" + name.substring(1, name.length() - 1);
                        final String var = name; name =StringUtils.replaceAll(name, "%(.+)?%", new Callback<String, Matcher>() {
                            @Nullable
                            @Override
                            public String run(final Matcher m) {
                                if(m.group(1).contains("{") || m.group(1).contains("}") || m.group(1).contains("%")) {
                                    Skript.error("'" + var + "' is not a valid name for a default variable");
                                    return null;
                                }
                                final ClassInfo<?> ci = Classes.getClassInfoFromUserInput(""+m.group(1));
                                if (ci == null) {
                                    Skript.error("Can't understand the type '" + m.group(1) + "'");
                                    return null; } return "<" + ci.getCodeName() + ">";
                            }
                        });
                        if (name == null) {
                            continue;
                        } else if (name.contains("%")) {
                            Skript.error("Invalid use of percent signs in variable name");
                            continue;
                        } if(Variables.getVariable(name, null, false) != null) continue; Object o;
                        final ParseLogHandler log = SkriptLogger.startParseLogHandler();
                        try {
                            o = Classes.parseSimple(((EntryNode) n).getValue(), Object.class, ParseContext.SCRIPT);
                            if (o == null) {
                                log.printError("Can't understand the value '" + ((EntryNode) n).getValue() + "'");
                                continue;
                            }
                            log.printLog();
                        } finally {
                            log.stop();
                        }
                        @SuppressWarnings("null")
                        final ClassInfo<?> ci = Classes.getSuperClassInfo(o.getClass()); if (ci.getSerializer() == null) {
                            Skript.error("Can't save '" + ((EntryNode) n).getValue() + "' in a variable");
                            continue;
                        } else if (ci.getSerializeAs() != null) {
                            final ClassInfo<?> as = Classes.getExactClassInfo(ci.getSerializeAs());
                            if (as == null) {
                                assert false : ci; continue;
                            }
                            o = Converters.convert(o, as.getC()); if (o == null) {
                                Skript.error("Can't save '" + ((EntryNode) n).getValue() + "' in a variable");
                                continue;
                            }
                        }
                        Variables.setVariable(name, o, null, false);
                    }
                    continue;
                }
                // Carregar todas funções
                for (SectionNode node : functionNodes) {
                    ScriptLoader.setCurrentEvent("function", FunctionEvent.class);
                    final Function<?> func = Functions.loadFunction(node);
                    if (func != null)
                        numFunctions++;
                    ScriptLoader.deleteCurrentEvent();
                }
                // Resolve forward references.
                Functions.resolvePendingReferences();
                // Carregar todos comandos
                for (SectionNode node : commandNodes) {
                    ScriptLoader.setCurrentEvent("command", CommandEvent.class);
                    final ScriptCommand cmd = Commands.loadCommand(node);
                    if (cmd != null) {
                        numCommands++;
                    }
                    ScriptLoader.deleteCurrentEvent();
                }

                // Carregar todos triggers
                for (SectionNode node : triggerNodes) {
                    String event = node.getKey();
                    if (event == null)
                        continue;
                    if (StringUtils.startsWithIgnoreCase(event, "on ")) event = event.substring(3);
                    event = ScriptLoader.replaceOptions(event);
                    NonNullPair<SkriptEventInfo<?>, SkriptEvent> parsedEvent = SkriptParser.parseEvent( event, "can't understand this event: '" + node.getKey() + "'" );
                    if (parsedEvent == null)
                        continue;
                    ScriptLoader.setCurrentEvent( parsedEvent.getFirst().getName().toLowerCase(Locale.ENGLISH), parsedEvent.getFirst().events );
                    final Trigger trigger;
                    try {
                        trigger = new Trigger(
                                config.getFile(),
                                event,
                                parsedEvent.getSecond(),
                                ScriptLoader.loadItems(node)
                        );
                    } finally {
                        ScriptLoader.deleteCurrentEvent();
                    }
                    if(parsedEvent.getSecond() instanceof SelfRegisteringSkriptEvent) {
                        ((SelfRegisteringSkriptEvent) parsedEvent.getSecond()).register(trigger);
                        SkriptEventHandler.addSelfRegisteringTrigger(trigger);
                    } else {
                        SkriptEventHandler.addTrigger(parsedEvent.getFirst().events, trigger);
                    } numTriggers++;
                } if (Skript.logHigh()) {
                    Skript.info( "loaded " + numTriggers + " trigger(s), " + numCommands + " command(s), " + numFunctions + " function(s) from '" + config.getFileName() + "'" );
                } ScriptLoader.currentScript = null;
            } finally {
                numErrors.stop();
            }
            return new ScriptInfo(1, numTriggers, numCommands, numFunctions);
        } catch (final IOException e) {
            Skript.error("Could not load " + f.getName() + ": " + ExceptionUtils.toString(e));
        } catch (final Exception e) {
            Skript.exception(e, "Could not load " + f.getName());
        } finally {
            SkriptLogger.setNode(null);
        }
        return new ScriptInfo();
    }


        static final ScriptInfo unloadScripts (File folder){
            ScriptInfo r = unloadScripts_(folder);
            Functions.validateFunctions();
            return r;
        }

        private final static ScriptInfo unloadScripts_ (File folder){
            ScriptInfo info = new ScriptInfo();
            File[] files = folder.listFiles(scriptFilter);

            assert files != null;
            for (File f : files) {
                if (f.isDirectory()) {
                    info.add(unloadScripts_(f));
                } else if (f.getName().endsWith(".sk")) {
                    info.add(unloadScript_(f));
                }
            }

            return info;
        }

        static final ScriptInfo unloadScript (File script){
            ScriptInfo r = unloadScript_(script);
            Functions.validateFunctions();
            return r;
        }

        private static final ScriptInfo unloadScript_ (File script){
            ScriptInfo info = SkriptEventHandler.removeTriggers(script);
            synchronized (loadedScripts) {
                loadedScripts.subtract(info);
                return info;
            }
        }

        public static final String replaceOptions (String s){
            String r = StringUtils.replaceAll(s, "\\{@(.+?)\\}", new Callback<String, Matcher>() {
                @Nullable
                public String run(Matcher m) {
                    String option = (String) ScriptLoader.currentOptions.get(m.group(1));
                    if (option == null) {
                        Skript.error("undefined option " + m.group());
                        return m.group();
                    } else {
                        return option;
                    }
                }
            });

            assert r != null;

            return r;
        }

        public static ArrayList<TriggerItem> loadItems (SectionNode node){
            if (Skript.debug()) {
                indentation = indentation + "    ";
            }

            ArrayList<TriggerItem> items = new ArrayList();
            Kleenean hadDelayBeforeLastIf = Kleenean.FALSE;

            for (Node n : node) {
                SkriptLogger.setNode(n);
                if (n instanceof SimpleNode) {
                    SimpleNode e = (SimpleNode) n;
                    String s = replaceOptions("" + e.getKey());
                    if (SkriptParser.validateLine(s)) {
                        Statement stmt = Statement.parse(s, "Can't understand this condition/effect: " + s);
                        if (stmt != null) {
                            if (Skript.debug() || n.debug()) {
                                Skript.debug(indentation + stmt.toString((Event) null, true));
                            }

                            items.add(stmt);
                            if (stmt instanceof Delay) {
                                hasDelayBefore = Kleenean.TRUE;
                            }
                        }
                    }
                } else if (n instanceof SectionNode) {
                    String name = replaceOptions("" + n.getKey());
                    if (SkriptParser.validateLine(name)) {
                        if (StringUtils.startsWithIgnoreCase(name, "loop ")) {
                            String l = "" + name.substring("loop ".length());
                            RetainingLogHandler h = SkriptLogger.startRetainingLog();

                            Expression<?> loopedExpr;
                            try {
                                loopedExpr = (new SkriptParser(l)).parseExpression(new Class[]{Object.class});
                                if (loopedExpr != null) {
                                    loopedExpr = loopedExpr.getConvertedExpression(new Class[]{Object.class});
                                }

                                if (loopedExpr == null) {
                                    h.printErrors("Can't understand this loop: '" + name + "'");
                                    continue;
                                }

                                h.printLog();
                            } finally {
                                h.stop();
                            }

                            if (loopedExpr.isSingle()) {
                                Skript.error("Can't loop " + loopedExpr + " because it's only a single value");
                            } else {
                                if (Skript.debug() || n.debug()) {
                                    Skript.debug(indentation + "loop " + loopedExpr.toString((Event) null, true) + ":");
                                }

                                Kleenean hadDelayBefore = hasDelayBefore;
                                items.add(new Loop(loopedExpr, (SectionNode) n));
                                if (hadDelayBefore != Kleenean.TRUE && hasDelayBefore != Kleenean.FALSE) {
                                    hasDelayBefore = Kleenean.UNKNOWN;
                                }
                            }
                        } else if (StringUtils.startsWithIgnoreCase(name, "while ")) {
                            String l = "" + name.substring("while ".length());
                            Condition c = Condition.parse(l, "Can't understand this condition: " + l);
                            if (c != null) {
                                if (Skript.debug() || n.debug()) {
                                    Skript.debug(indentation + "while " + c.toString((Event) null, true) + ":");
                                }

                                Kleenean hadDelayBefore = hasDelayBefore;
                                items.add(new While(c, (SectionNode) n));
                                if (hadDelayBefore != Kleenean.TRUE && hasDelayBefore != Kleenean.FALSE) {
                                    hasDelayBefore = Kleenean.UNKNOWN;
                                }
                            }
                        } else if (name.equalsIgnoreCase("else")) {
                            if (!items.isEmpty() && items.get(items.size() - 1) instanceof Conditional && !((Conditional) items.get(items.size() - 1)).hasElseClause()) {
                                if (Skript.debug() || n.debug()) {
                                    Skript.debug(indentation + "else:");
                                }

                                Kleenean hadDelayAfterLastIf = hasDelayBefore;
                                hasDelayBefore = hadDelayBeforeLastIf;
                                ((Conditional) items.get(items.size() - 1)).loadElseClause((SectionNode) n);
                                hasDelayBefore = hadDelayBeforeLastIf.or(hadDelayAfterLastIf.and(hasDelayBefore));
                            } else {
                                Skript.error("'else' has to be placed just after an 'if' or 'else if' section");
                            }
                        } else if (StringUtils.startsWithIgnoreCase(name, "else if ")) {
                            if (!items.isEmpty() && items.get(items.size() - 1) instanceof Conditional && !((Conditional) items.get(items.size() - 1)).hasElseClause()) {
                                name = "" + name.substring("else if ".length());
                                Condition cond = Condition.parse(name, "can't understand this condition: '" + name + "'");
                                if (cond != null) {
                                    if (Skript.debug() || n.debug()) {
                                        Skript.debug(indentation + "else if " + cond.toString((Event) null, true));
                                    }

                                    Kleenean hadDelayAfterLastIf = hasDelayBefore;
                                    hasDelayBefore = hadDelayBeforeLastIf;
                                    ((Conditional) items.get(items.size() - 1)).loadElseIf(cond, (SectionNode) n);
                                    hasDelayBefore = hadDelayBeforeLastIf.or(hadDelayAfterLastIf.and(hasDelayBefore.and(Kleenean.UNKNOWN)));
                                }
                            } else {
                                Skript.error("'else if' has to be placed just after another 'if' or 'else if' section");
                            }
                        } else {
                            if (StringUtils.startsWithIgnoreCase(name, "if ")) {
                                name = "" + name.substring(3);
                            }

                            Condition cond = Condition.parse(name, "can't understand this condition: '" + name + "'");
                            if (cond != null) {
                                if (Skript.debug() || n.debug()) {
                                    Skript.debug(indentation + cond.toString((Event) null, true) + ":");
                                }

                                Kleenean hadDelayBefore = hasDelayBefore;
                                hadDelayBeforeLastIf = hadDelayBefore;
                                items.add(new Conditional(cond, (SectionNode) n));
                                hasDelayBefore = hadDelayBefore.or(hasDelayBefore.and(Kleenean.UNKNOWN));
                            }
                        }
                    }
                }
            }

            for (int i = 0; i < items.size() - 1; ++i) {
                ((TriggerItem) items.get(i)).setNext((TriggerItem) items.get(i + 1));
            }

            SkriptLogger.setNode(node);
            if (Skript.debug()) {
                indentation = "" + indentation.substring(0, indentation.length() - 4);
            }

            return items;
        }

        @Nullable
        static Trigger loadTrigger (SectionNode node){
            String event = node.getKey();
            if (event == null) {
                assert false : node;

                return null;
            } else {
                if (event.toLowerCase().startsWith("on ")) {
                    event = "" + event.substring("on ".length());
                }

                NonNullPair<SkriptEventInfo<?>, SkriptEvent> parsedEvent = SkriptParser.parseEvent(event, "can't understand this event: '" + node.getKey() + "'");
                if (parsedEvent == null) {
                    assert false;

                    return null;
                } else {
                    setCurrentEvent("unit test", ((SkriptEventInfo) parsedEvent.getFirst()).events);

                    Trigger var3;
                    try {
                        var3 = new Trigger((File) null, event, (SkriptEvent) parsedEvent.getSecond(), loadItems(node));
                    } finally {
                        deleteCurrentEvent();
                    }

                    return var3;
                }
            }
        }

        public static final int loadedScripts () {
            synchronized (loadedScripts) {
                return loadedScripts.files;
            }
        }

        public static final int loadedCommands () {
            synchronized (loadedScripts) {
                return loadedScripts.commands;
            }
        }

        public static final int loadedFunctions () {
            synchronized (loadedScripts) {
                return loadedScripts.functions;
            }
        }

        public static final int loadedTriggers () {
            synchronized (loadedScripts) {
                return loadedScripts.triggers;
            }
        }

        public static final boolean isCurrentEvent (@Nullable Class < ? extends Event > event){
            return CollectionUtils.containsSuperclass(currentEvents, event);
        }

        @SafeVarargs
        public static final boolean isCurrentEvent (Class < ? extends Event >...events){
            return CollectionUtils.containsAnySuperclass(currentEvents, events);
        }

        @Nullable
        public static Class<? extends Event>[] getCurrentEvents () {
            return currentEvents;
        }

        static {
            hasDelayBefore = Kleenean.FALSE;
            indentation = "";
            scriptFilter = new FileFilter() {
                public boolean accept(@Nullable File f) {
                    return f != null && (f.isDirectory() || StringUtils.endsWithIgnoreCase("" + f.getName(), ".sk")) && !f.getName().startsWith("-");
                }
            };
        }

        public static class ScriptInfo {
            public int files;
            public int triggers;
            public int commands;
            public int functions;

            public ScriptInfo() {
            }

            public ScriptInfo(int numFiles, int numTriggers, int numCommands, int numFunctions) {
                this.files = numFiles;
                this.triggers = numTriggers;
                this.commands = numCommands;
                this.functions = numFunctions;
            }

            public void add(ScriptInfo other) {
                this.files += other.files;
                this.triggers += other.triggers;
                this.commands += other.commands;
                this.functions += other.functions;
            }

            public void subtract(ScriptInfo other) {
                this.files -= other.files;
                this.triggers -= other.triggers;
                this.commands -= other.commands;
                this.functions -= other.functions;
            }
        }
    }
