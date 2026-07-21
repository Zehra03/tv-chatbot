"use client";

import { useEffect, useId, useRef, useCallback, useTransition } from "react";
import { useState } from "react";
import { cn } from "@/lib/utils";
import {
    Plane,
    Hotel,
    CalendarCheck,
    SendIcon,
    LoaderIcon,
    Sparkles,
} from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";
import * as React from "react"
import { CHAT_COMMANDS } from "@/features/chat/commands";

interface UseAutoResizeTextareaProps {
    minHeight: number;
    maxHeight?: number;
}

function useAutoResizeTextarea({
    minHeight,
    maxHeight,
}: UseAutoResizeTextareaProps) {
    const textareaRef = useRef<HTMLTextAreaElement>(null);

    const adjustHeight = useCallback(
        (reset?: boolean) => {
            const textarea = textareaRef.current;
            if (!textarea) return;

            if (reset) {
                textarea.style.height = `${minHeight}px`;
                return;
            }

            textarea.style.height = `${minHeight}px`;
            const newHeight = Math.max(
                minHeight,
                Math.min(
                    textarea.scrollHeight,
                    maxHeight ?? Number.POSITIVE_INFINITY
                )
            );

            textarea.style.height = `${newHeight}px`;
        },
        [minHeight, maxHeight]
    );

    useEffect(() => {
        const textarea = textareaRef.current;
        if (textarea) {
            textarea.style.height = `${minHeight}px`;
        }
    }, [minHeight]);

    useEffect(() => {
        const handleResize = () => adjustHeight();
        window.addEventListener("resize", handleResize);
        return () => window.removeEventListener("resize", handleResize);
    }, [adjustHeight]);

    return { textareaRef, adjustHeight };
}

/** Komut → ikon eşlemesi. Komut verisinin (label/prefix/action) TEK kaynağı
 * `@/features/chat/commands`; ikonlar JSX olduğu için burada, prefix ile eşlenir. */
const COMMAND_ICONS: Record<string, React.ReactNode> = {
    "/otel": <Hotel className="w-4 h-4" />,
    "/ucus": <Plane className="w-4 h-4" />,
    "/rezervasyon": <CalendarCheck className="w-4 h-4" />,
    "/oneri": <Sparkles className="w-4 h-4" />,
};

interface TextareaProps
  extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  containerClassName?: string;
  showRing?: boolean;
}

const Textarea = React.forwardRef<HTMLTextAreaElement, TextareaProps>(
  ({ className, containerClassName, showRing = true, ...props }, ref) => {
    const [isFocused, setIsFocused] = React.useState(false);
    
    return (
      <div className={cn(
        "relative",
        containerClassName
      )}>
        <textarea
          className={cn(
            "flex min-h-[80px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm",
            "transition-all duration-200 ease-in-out",
            "placeholder:text-muted-foreground",
            "disabled:cursor-not-allowed disabled:opacity-50",
            showRing ? "focus-visible:outline-none focus-visible:ring-0 focus-visible:ring-offset-0" : "",
            className
          )}
          ref={ref}
          onFocus={() => setIsFocused(true)}
          onBlur={() => setIsFocused(false)}
          {...props}
        />
        
        {showRing && isFocused && (
          <motion.span 
            className="absolute inset-0 rounded-md pointer-events-none ring-2 ring-offset-0 ring-primary/30"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
          />
        )}

        {props.onChange && (
          <div 
            className="absolute bottom-2 right-2 opacity-0 w-2 h-2 bg-primary rounded-full"
            style={{
              animation: 'none',
            }}
            id="textarea-ripple"
          />
        )}
      </div>
    )
  }
)
Textarea.displayName = "Textarea"

/**
 * Boş sohbetin hero composer'ı — 21st.dev "Animated AI Chat"ten uyarlandı.
 * Kendi arka plan katmanı YOK: kök şeffaftır, Layout'taki NightSkyBackground
 * arkadan görünür. `onSend` verilirse gerçek akışa bağlanır (Composer ile aynı
 * sözleşme); verilmezse playground için gönderimi simüle eder.
 * `hero=false` (thread modu): başlık ve hızlı eylemler kapanır, aynı cam kart
 * alta inip kalıcı composer olur — ilk mesajda bileşen sökülmez, bozulmaz.
 */
interface AnimatedAIChatProps {
    onSend?: (text: string) => void;
    disabled?: boolean;
    placeholder?: string;
    hero?: boolean;
}

/** Tek mesajın azami uzunluğu. */
const MAX_LENGTH = 2000;
/** Sayaç bu uzunluktan sonra görünür — sınıra yaklaşmadan gürültü yapmasın. */
const COUNTER_VISIBLE_AT = 1800;

export function AnimatedAIChat({ onSend, disabled, placeholder, hero = true }: AnimatedAIChatProps = {}) {
    const [value, setValue] = useState("");
    const [isTyping, setIsTyping] = useState(false);
    const [, startTransition] = useTransition();
    const [activeSuggestion, setActiveSuggestion] = useState<number>(-1);
    const [showCommandPalette, setShowCommandPalette] = useState(false);
    const [, setRecentCommand] = useState<string | null>(null);
    const { textareaRef, adjustHeight } = useAutoResizeTextarea({
        minHeight: 44,
        maxHeight: 200,
    });
    const commandPaletteRef = useRef<HTMLDivElement>(null);
    /** listbox ↔ textarea bağı (aria-controls / aria-activedescendant) için kararlı id. */
    const commandPaletteId = useId();

    // Komut listesi (label/prefix/action) tek kaynaktan gelir; resolveCommand ile
    // aynı veriyi paylaşır ki render edilen kart ile gönderilen intent hiç ayrışmasın.
    const commandSuggestions = CHAT_COMMANDS;

    useEffect(() => {
        if (value.startsWith('/') && !value.includes(' ')) {
            setShowCommandPalette(true);
            
            const matchingSuggestionIndex = CHAT_COMMANDS.findIndex(
                (cmd) => cmd.prefix.startsWith(value)
            );
            
            if (matchingSuggestionIndex >= 0) {
                setActiveSuggestion(matchingSuggestionIndex);
            } else {
                setActiveSuggestion(-1);
            }
        } else {
            setShowCommandPalette(false);
        }
    }, [value]);

    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            const target = event.target as Node;
            if (commandPaletteRef.current && !commandPaletteRef.current.contains(target)) {
                setShowCommandPalette(false);
            }
        };

        document.addEventListener('mousedown', handleClickOutside);
        return () => {
            document.removeEventListener('mousedown', handleClickOutside);
        };
    }, []);

    // Mesaj gönderilince composer pending boyunca `disabled` olur ve odağı yitirir
    // (Gönder'e tıklandıysa odak zaten butondadır). Yanıt gelip alan tekrar
    // etkinleşince (disabled true→false) imleci input'a geri getir — kullanıcı her
    // mesajdan sonra sohbet balonuna yeniden tıklamak zorunda kalmasın (madde 11).
    const prevDisabled = useRef(disabled);
    useEffect(() => {
        if (prevDisabled.current && !disabled) {
            textareaRef.current?.focus();
        }
        prevDisabled.current = disabled;
    }, [disabled, textareaRef]);

    const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
        if (showCommandPalette) {
            if (e.key === 'ArrowDown') {
                e.preventDefault();
                setActiveSuggestion(prev => 
                    prev < commandSuggestions.length - 1 ? prev + 1 : 0
                );
            } else if (e.key === 'ArrowUp') {
                e.preventDefault();
                setActiveSuggestion(prev => 
                    prev > 0 ? prev - 1 : commandSuggestions.length - 1
                );
            } else if (e.key === 'Tab' || e.key === 'Enter') {
                // Enter/Tab YALNIZCA gerçek bir seçim varken yakalanır. Eskiden koşulsuz
                // preventDefault ediliyordu: eşleşen komut yokken (ör. "/merhaba" → palet
                // açık ama activeSuggestion -1) Enter hiçbir şey yapmadan yutuluyor,
                // mesaj klavyeyle GÖNDERİLEMİYOR ve kullanıcı hiçbir geri bildirim almıyordu.
                if (activeSuggestion >= 0) {
                    e.preventDefault();
                    selectCommandSuggestion(activeSuggestion);
                } else if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    setShowCommandPalette(false);
                    if (value.trim()) {
                        handleSendMessage();
                    }
                }
                // Seçim yokken Tab varsayılanına bırakılır — odak normal şekilde ilerlesin.
            } else if (e.key === 'Escape') {
                e.preventDefault();
                setShowCommandPalette(false);
            }
        } else if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            if (value.trim()) {
                handleSendMessage();
            }
        }
    };

    const handleSendMessage = () => {
        const trimmed = value.trim();
        if (!trimmed || disabled) return;
        if (onSend) {
            // Gerçek akış: mesajı üst bileşene teslim et, alanı temizle.
            onSend(trimmed);
            setValue("");
            adjustHeight(true);
            // İmleç sohbette kalsın: Gönder'e tıklandıysa odağı butondan input'a
            // al. Pending'e girilirse `disabled` bunu blur eder; yanıt gelince
            // yukarıdaki effect odağı geri getirir (madde 11).
            textareaRef.current?.focus();
            return;
        }
        // Playground: backend yokken gönderimi simüle et.
        startTransition(() => {
            setIsTyping(true);
            setTimeout(() => {
                setIsTyping(false);
                setValue("");
                adjustHeight(true);
            }, 3000);
        });
    };


    // Bir komutu HEMEN çalıştırır: prefix'i üst bileşene teslim eder (onSend =
    // ChatPage.handleSend → resolveCommand ile arama komutu doğal cümleye çevrilir,
    // "/rezervasyon" ise /reservations'a yönlendirir). Playground'da (onSend yok)
    // yalnız prefill yapar.
    const runCommand = (prefix: string) => {
        setShowCommandPalette(false);
        const trimmed = prefix.trim();
        if (!trimmed || disabled) return;
        if (onSend) {
            onSend(trimmed);
            setValue("");
            adjustHeight(true);
            textareaRef.current?.focus();
            return;
        }
        setValue(prefix + ' ');
    };

    // Komut paletinden seçim: yönlendirme komutu (ör. /rezervasyon) anında
    // çalışır; arama komutu ise otomatik-tamamlanır (kullanıcı şehir/tarih
    // eklesin) — hızlı-eylem kartları ise her zaman anında çalışır (runCommand).
    const selectCommandSuggestion = (index: number) => {
        const selectedCommand = commandSuggestions[index];
        if (selectedCommand.action.type === 'navigate') {
            runCommand(selectedCommand.prefix);
        } else {
            setValue(selectedCommand.prefix + ' ');
            setShowCommandPalette(false);
        }
        setRecentCommand(selectedCommand.label);
        setTimeout(() => setRecentCommand(null), 2000);
    };

    // Orijinali min-h-screen + kendi blob katmanı; kök h-full/şeffaf yapıldı,
    // blob'lar kaldırıldı — sayfanın NightSkyBackground'u arkadan görünür.
    // Thread modunda (hero=false) kök dikeyde büyümez, palet yukarı taşabilsin
    // diye overflow serbest kalır.
    return (
        <div className={cn(
            "flex flex-col w-full items-center bg-transparent text-foreground relative",
            hero && "h-full min-h-full flex-1 justify-center p-6 overflow-hidden"
        )}>
            <div className={cn("w-full mx-auto relative", hero && "max-w-2xl")}>
                <motion.div
                    className="relative z-10 space-y-12"
                    initial={{ opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.6, ease: "easeOut" }}
                >
                    <AnimatePresence>
                        {hero && (
                            <motion.div
                                key="hero-header"
                                className="text-center space-y-3 overflow-hidden"
                                exit={{ opacity: 0, height: 0, y: -8 }}
                                transition={{ duration: 0.25, ease: "easeIn" }}
                            >
                                <motion.div
                                    initial={{ opacity: 0, y: 10 }}
                                    animate={{ opacity: 1, y: 0 }}
                                    transition={{ delay: 0.2, duration: 0.5 }}
                                    className="inline-block"
                                >
                                    <h1 className="text-3xl font-medium tracking-tight text-foreground pb-1">
                                        Bugün nereye gidiyoruz?
                                    </h1>
                                    <motion.div
                                        className="h-px bg-gradient-to-r from-transparent via-foreground/20 to-transparent"
                                        initial={{ width: 0, opacity: 0 }}
                                        animate={{ width: "100%", opacity: 1 }}
                                        transition={{ delay: 0.5, duration: 0.8 }}
                                    />
                                </motion.div>
                                <motion.p
                                    className="text-sm text-foreground/40"
                                    initial={{ opacity: 0 }}
                                    animate={{ opacity: 1 }}
                                    transition={{ delay: 0.3 }}
                                >
                                    Bir komut yazın ya da sorunuzu sorun
                                </motion.p>
                            </motion.div>
                        )}
                    </AnimatePresence>

                    <motion.div
                        layout
                        className="relative bg-card rounded-2xl border border-border shadow-soft"
                        initial={{ scale: 0.98 }}
                        animate={{ scale: 1 }}
                        transition={{ delay: 0.1, layout: { duration: 0.35, ease: "easeOut" } }}
                    >
                        <AnimatePresence>
                            {showCommandPalette && (
                                <motion.div
                                    ref={commandPaletteRef}
                                    className="absolute left-4 right-4 bottom-full mb-2 bg-popover rounded-lg z-50 shadow-soft-lg border border-border overflow-hidden"
                                    initial={{ opacity: 0, y: 5 }}
                                    animate={{ opacity: 1, y: 0 }}
                                    exit={{ opacity: 0, y: 5 }}
                                    transition={{ duration: 0.15 }}
                                >
                                    {/* WAI-ARIA combobox deseni — LocationAutocomplete ile aynı.
                                        Eskiden satırlar rolsüz, tıklama-only div'lerdi: ok tuşları
                                        `activeSuggestion`ı değiştiriyordu ama bu yalnız bir arka plan
                                        rengiydi; ekran okuyucu ne listeyi ne de aktif seçeneği
                                        duyuruyordu. */}
                                    <div className="py-1 bg-popover" role="listbox" id={commandPaletteId} aria-label="Sohbet komutları">
                                        {commandSuggestions.map((suggestion, index) => (
                                            <motion.div
                                                key={suggestion.prefix}
                                                id={`${commandPaletteId}-opt-${index}`}
                                                role="option"
                                                aria-selected={activeSuggestion === index}
                                                className={cn(
                                                    "flex items-center gap-2 px-3 py-2 text-xs transition-colors cursor-pointer",
                                                    activeSuggestion === index
                                                        ? "bg-accent text-accent-foreground"
                                                        : "text-muted-foreground hover:bg-accent"
                                                )}
                                                onClick={() => selectCommandSuggestion(index)}
                                                initial={{ opacity: 0 }}
                                                animate={{ opacity: 1 }}
                                                transition={{ delay: index * 0.03 }}
                                            >
                                                <div className="w-5 h-5 flex items-center justify-center text-foreground/60" aria-hidden>
                                                    {COMMAND_ICONS[suggestion.prefix]}
                                                </div>
                                                <div className="font-medium">{suggestion.label}</div>
                                                <div className="text-foreground/40 text-xs ml-1">
                                                    {suggestion.prefix}
                                                </div>
                                            </motion.div>
                                        ))}
                                    </div>
                                </motion.div>
                            )}
                        </AnimatePresence>

                        <div className="p-2">
                            <Textarea
                                ref={textareaRef}
                                value={value}
                                onChange={(e) => {
                                    setValue(e.target.value);
                                    adjustHeight();
                                }}
                                onKeyDown={handleKeyDown}
                                aria-label="Mesaj"
                                // Palet açıkken alan bir combobox gibi davranır: ok tuşlarıyla
                                // gezilen seçenek aria-activedescendant ile duyurulur (odak
                                // textarea'da kalır, WAI-ARIA combobox deseni).
                                role="combobox"
                                aria-expanded={showCommandPalette}
                                aria-controls={commandPaletteId}
                                aria-autocomplete="list"
                                aria-activedescendant={
                                    showCommandPalette && activeSuggestion >= 0
                                        ? `${commandPaletteId}-opt-${activeSuggestion}`
                                        : undefined
                                }
                                disabled={disabled}
                                maxLength={MAX_LENGTH}
                                placeholder={placeholder ?? "PaxAssist'e sor — otel, uçuş, tatil önerisi..."}
                                containerClassName="w-full"
                                className={cn(
                                    "w-full px-4 py-2",
                                    "resize-none",
                                    "bg-transparent",
                                    "border-none",
                                    "text-foreground/90 text-sm",
                                    "focus:outline-none",
                                    "placeholder:text-foreground/20",
                                    "min-h-[44px]"
                                )}
                                style={{
                                    overflow: "hidden",
                                }}
                                showRing={false}
                            />
                            {value.length >= COUNTER_VISIBLE_AT && (
                                // aria-live=polite: sınıra yaklaşan görme engelli kullanıcı da
                                // duysun; her tuşta değil, okuyucunun uygun bulduğu anda okunur.
                                <p
                                    aria-live="polite"
                                    className={cn(
                                        "px-4 pt-1 text-end text-xs tabular-nums",
                                        value.length >= MAX_LENGTH ? "text-destructive-emphasis" : "text-foreground/40"
                                    )}
                                >
                                    {value.length}/{MAX_LENGTH}
                                </p>
                            )}
                        </div>

                        {/* Komut butonu kaldırıldı — palet "/" ile ve alttaki hızlı-eylem
                            kartlarıyla açılır; alt çubukta yalnız Gönder kalır (sağa yaslı). */}
                        <div className="px-3 py-2 border-t border-border flex items-center justify-end gap-4">
                            <motion.button
                                type="button"
                                onClick={handleSendMessage}
                                whileHover={{ scale: 1.01 }}
                                whileTap={{ scale: 0.98 }}
                                disabled={isTyping || disabled || !value.trim()}
                                className={cn(
                                    "px-4 py-2 rounded-lg text-sm font-medium transition-all",
                                    "flex items-center gap-2",
                                    // Etkin "Gönder": zıt yüzey (koyuda beyaz/lacivert,
                                    // açıkta lacivert/beyaz) — token'lardan.
                                    value.trim()
                                        ? "bg-foreground text-background shadow-lg shadow-foreground/10"
                                        : "bg-muted text-foreground/40"
                                )}
                            >
                                {isTyping || disabled ? (
                                    <LoaderIcon className="w-4 h-4 animate-[spin_2s_linear_infinite]" />
                                ) : (
                                    <SendIcon className="w-4 h-4" />
                                )}
                                <span>Gönder</span>
                            </motion.button>
                        </div>
                    </motion.div>

                    <AnimatePresence>
                        {hero && (
                            <motion.div
                                key="quick-actions"
                                className="flex flex-wrap items-center justify-center gap-2 overflow-hidden"
                                exit={{ opacity: 0, height: 0 }}
                                transition={{ duration: 0.25, ease: "easeIn" }}
                            >
                                {commandSuggestions.map((suggestion, index) => (
                                    <motion.button
                                        key={suggestion.prefix}
                                        type="button"
                                        onClick={() => runCommand(suggestion.prefix)}
                                        disabled={disabled}
                                        className="flex items-center gap-2 px-3 py-2 bg-muted hover:bg-accent rounded-lg text-sm text-foreground/60 hover:text-foreground/90 transition-all relative group disabled:opacity-50 disabled:cursor-not-allowed"
                                        initial={{ opacity: 0, y: 10 }}
                                        animate={{ opacity: 1, y: 0 }}
                                        transition={{ delay: index * 0.1 }}
                                    >
                                        {COMMAND_ICONS[suggestion.prefix]}
                                        <span>{suggestion.label}</span>
                                        <motion.div
                                            className="absolute inset-0 border border-border rounded-lg"
                                            initial={false}
                                            animate={{
                                                opacity: [0, 1],
                                                scale: [0.98, 1],
                                            }}
                                            transition={{
                                                duration: 0.3,
                                                ease: "easeOut",
                                            }}
                                        />
                                    </motion.button>
                                ))}
                            </motion.div>
                        )}
                    </AnimatePresence>
                </motion.div>
            </div>

            <AnimatePresence>
                {isTyping && (
                    <motion.div 
                        className="fixed bottom-8 mx-auto transform -translate-x-1/2 bg-card rounded-full px-4 py-2 shadow-soft border border-border"
                        initial={{ opacity: 0, y: 20 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, y: 20 }}
                    >
                        <div className="flex items-center gap-3">
                            <div className="w-8 h-7 rounded-full bg-muted flex items-center justify-center text-center">
                                <span className="text-xs font-medium text-foreground/90 mb-0.5">Pax</span>
                            </div>
                            <div className="flex items-center gap-2 text-sm text-foreground/70">
                                <span>Düşünüyor</span>
                                <TypingDots />
                            </div>
                        </div>
                    </motion.div>
                )}
            </AnimatePresence>

        </div>
    );
}

function TypingDots() {
    return (
        <div className="flex items-center ml-1">
            {[1, 2, 3].map((dot) => (
                <motion.div
                    key={dot}
                    className="w-1.5 h-1.5 bg-foreground/90 rounded-full mx-0.5"
                    initial={{ opacity: 0.3 }}
                    animate={{ 
                        opacity: [0.3, 0.9, 0.3],
                        scale: [0.85, 1.1, 0.85]
                    }}
                    transition={{
                        duration: 1.2,
                        repeat: Infinity,
                        delay: dot * 0.15,
                        ease: "easeInOut",
                    }}
                    style={{
                        // Nokta halesi ön plan renginden — açık temada beyaz hale görünmezdi.
                        boxShadow: "0 0 4px hsl(var(--foreground) / 0.3)"
                    }}
                />
            ))}
        </div>
    );
}


const rippleKeyframes = `
@keyframes ripple {
  0% { transform: scale(0.5); opacity: 0.6; }
  100% { transform: scale(2); opacity: 0; }
}
`;

if (typeof document !== 'undefined') {
    const style = document.createElement('style');
    style.innerHTML = rippleKeyframes;
    document.head.appendChild(style);
}


