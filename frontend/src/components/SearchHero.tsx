import type { ReactNode } from 'react'
import { motion } from 'framer-motion'
import { SplitText } from '@/components/SplitText'

/**
 * Skyscanner tarzı arama hero'su — fotoğraf arka plan + lacivert degrade örtü
 * üzerinde başlık ve arama formu (Oteller / Uçuşlar sayfaları).
 *
 * overflow-hidden BİLEREK yok: takvim ve misafir popover'ları kartın dışına
 * taşabilmeli. Yuvarlak köşeler bu yüzden mutlak katmanlara (görsel + örtü)
 * ayrı ayrı uygulanır.
 *
 * TEMADAN BAĞIMSIZ: örtü her temada lacivert (fotoğrafın üstü), dolayısıyla
 * buradaki metin/kenarlar `text-foreground` gibi token'lar DEĞİL, sabit beyaz/ice
 * olmalı — açık temada token'a bağlansaydı lacivert örtü üzerinde siyah yazı
 * çıkardı. Aynı gerekçe alanlar için `heroFieldClass`'ta da geçerli.
 */
interface SearchHeroProps {
  /** Arka plan fotoğrafı (import edilmiş asset URL'i). */
  image: string
  title: string
  subtitle?: string
  /** Arama formu — örtünün üzerinde render edilir. */
  children?: ReactNode
}

export function SearchHero({ image, title, subtitle, children }: SearchHeroProps) {
  return (
    <motion.section
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3, ease: 'easeOut' }}
      className="relative"
    >
      <img
        src={image}
        alt=""
        aria-hidden="true"
        className="absolute inset-0 h-full w-full rounded-3xl border border-white/10 object-cover"
      />
      {/* Soldan sağa açılan örtü: başlık/etiketler okunur kalır, fotoğraf sağda görünür. */}
      <div
        aria-hidden="true"
        className="absolute inset-0 rounded-3xl bg-gradient-to-r from-brand-navy/90 via-brand-navy/60 to-brand-navy/30"
      />
      {/* text-white: örtü her temada lacivert, dolayısıyla İÇERİĞİN TAMAMI koyu
          zemin üstünde. Alan etiketleri (ui/label) kendi rengini taşımaz, buradan
          miras alır — bu satır olmadan açık temada Layout'un `text-foreground`'unu
          (siyah) miras alıp lacivert örtüde okunmaz oluyorlardı. Kendi rengini
          belirleyen çocuklar (heroFieldClass'lı alanlar, .pax-popover'lar) etkilenmez. */}
      <div className="relative p-5 text-white sm:p-8 lg:p-10">
        <SplitText
          text={title}
          tag="h1"
          textAlign="left"
          className="text-3xl font-bold text-white sm:text-4xl"
          delay={40}
          duration={0.8}
        />
        <div
          aria-hidden="true"
          className="mt-2 h-1 w-16 rounded-full bg-gradient-to-r from-brand-blue to-brand-steel"
        />
        {subtitle && <p className="mt-2 max-w-2xl text-sm text-brand-cream/80">{subtitle}</p>}
        <div className="mt-6">{children}</div>
      </div>
    </motion.section>
  )
}

export default SearchHero
