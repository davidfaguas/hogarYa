import React, { useState, useEffect } from 'react';
import { Coins, ArrowRightLeft, Sparkles, ChevronUp, ChevronDown } from 'lucide-react';
import { CURRENT_UF_VALUE_CLP, formatCLP } from '../utils/realEstateCalculations';

interface FloatingCurrencyBarProps {
  priceDisplayMode: 'uf' | 'clp';
  onTogglePriceDisplayMode: () => void;
  isMobileSimulated?: boolean;
}

export const FloatingCurrencyBar: React.FC<FloatingCurrencyBarProps> = ({
  priceDisplayMode,
  onTogglePriceDisplayMode,
  isMobileSimulated = false
}) => {
  const [scrolled, setScrolled] = useState(false);
  const [minimized, setMinimized] = useState(false);

  useEffect(() => {
    const handleScroll = () => {
      // Show or highlight once scrolled past 150px
      if (window.scrollY > 150) {
        setScrolled(true);
      } else {
        setScrolled(false);
      }
    };
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  return (
    <aside 
      aria-label="Control de moneda flotante"
      className={`fixed z-40 transition-all duration-300 pointer-events-auto ${
        isMobileSimulated
          ? 'bottom-20 right-4'
          : 'bottom-6 right-6 md:bottom-8 md:right-8'
      }`}
    >
      <div className="bg-white/95 backdrop-blur-md border border-slate-200/90 shadow-xl rounded-2xl p-2 sm:p-2.5 flex items-center gap-2.5 text-slate-800 ring-1 ring-black/5 animate-in fade-in slide-in-from-bottom-3 duration-300">
        
        {/* Currency Icon */}
        <div className="w-8 h-8 rounded-xl bg-[#0d9488]/10 text-[#0d9488] flex items-center justify-center shrink-0">
          <Coins className="w-4 h-4" />
        </div>

        {/* Informative text & rate */}
        {!minimized && (
          <div className="hidden sm:flex flex-col leading-tight pr-1">
            <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
              Moneda de visualización
            </span>
            <span className="text-xs font-semibold text-slate-700">
              1 UF ≈ {formatCLP(CURRENT_UF_VALUE_CLP)}
            </span>
          </div>
        )}

        {/* The Toggle Button (UF ⇄ CLP) */}
        <div className="flex items-center bg-slate-100 p-0.5 rounded-xl border border-slate-200/80">
          <button
            type="button"
            onClick={() => {
              if (priceDisplayMode !== 'uf') onTogglePriceDisplayMode();
            }}
            className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all cursor-pointer ${
              priceDisplayMode === 'uf'
                ? 'bg-[#1e293b] text-white shadow-xs'
                : 'text-slate-600 hover:text-slate-900'
            }`}
            aria-label="Ver precios en Unidades de Fomento (UF)"
          >
            UF
          </button>
          <button
            type="button"
            onClick={() => {
              if (priceDisplayMode !== 'clp') onTogglePriceDisplayMode();
            }}
            className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all cursor-pointer ${
              priceDisplayMode === 'clp'
                ? 'bg-[#0d9488] text-white shadow-xs'
                : 'text-slate-600 hover:text-slate-900'
            }`}
            aria-label="Ver precios en Pesos Chilenos (CLP)"
          >
            CLP ($)
          </button>
        </div>

        {/* Quick minimize toggle button */}
        <button
          type="button"
          onClick={() => setMinimized(!minimized)}
          className="text-slate-400 hover:text-slate-600 p-1 rounded-md transition-colors hidden sm:block"
          title={minimized ? "Expandir barra de moneda" : "Minimizar"}
        >
          {minimized ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
        </button>

      </div>
    </aside>
  );
};
