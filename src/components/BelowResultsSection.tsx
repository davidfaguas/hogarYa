import React, { useState } from 'react';
import { 
  ChevronDown, 
  ChevronUp, 
  ShieldCheck, 
  MapPin, 
  Mail, 
  ChevronLeft, 
  ChevronRight,
  Home
} from 'lucide-react';
import { CHILE_COMMUNES } from '../data/mockProperties';

interface BelowResultsSectionProps {
  onSelectCommune: (commune: string) => void;
  onOpenMortgageSimulator: () => void;
  onLoadMore?: () => void;
  currentPage?: number;
}

export const BelowResultsSection: React.FC<BelowResultsSectionProps> = ({
  onSelectCommune,
  onOpenMortgageSimulator,
}) => {
  const [activePage, setActivePage] = useState(1);
  const [seoExpanded, setSeoExpanded] = useState(false);

  const pages = [1, 2, 3, 4, 5];

  return (
    <div className="mt-10 space-y-10">
      
      {/* 1. Pagination */}
      <div className="flex flex-col items-center justify-center gap-3 pt-6 border-t border-slate-200">
        <nav aria-label="Paginación de resultados" className="flex items-center gap-1 sm:gap-2">
          {/* Previous Page */}
          <button
            type="button"
            disabled={activePage === 1}
            onClick={() => {
              setActivePage((p) => Math.max(1, p - 1));
              window.scrollTo({ top: 350, behavior: 'smooth' });
            }}
            className={`w-9 h-9 rounded-full flex items-center justify-center transition-colors ${
              activePage === 1
                ? 'text-slate-300 cursor-not-allowed'
                : 'text-slate-700 hover:bg-slate-100 cursor-pointer'
            }`}
            aria-label="Página anterior"
          >
            <ChevronLeft className="w-5 h-5" />
          </button>

          {/* Page numbers */}
          {pages.map((p) => (
            <button
              key={p}
              type="button"
              onClick={() => {
                setActivePage(p);
                window.scrollTo({ top: 350, behavior: 'smooth' });
              }}
              className={`w-9 h-9 rounded-full text-xs font-bold transition-all cursor-pointer ${
                activePage === p
                  ? 'bg-[#0f172a] text-white shadow-sm'
                  : 'text-slate-700 hover:bg-slate-100'
              }`}
              aria-label={`Página ${p}`}
              aria-current={activePage === p ? 'page' : undefined}
            >
              {p}
            </button>
          ))}

          <span className="px-1 text-slate-400 font-bold">...</span>

          {/* Last page */}
          <button
            type="button"
            onClick={() => {
              setActivePage(1426);
              window.scrollTo({ top: 350, behavior: 'smooth' });
            }}
            className={`w-10 h-9 rounded-full text-xs font-bold transition-all cursor-pointer ${
              activePage === 1426
                ? 'bg-[#0f172a] text-white'
                : 'text-slate-700 hover:bg-slate-100'
            }`}
          >
            1426
          </button>

          {/* Next Page */}
          <button
            type="button"
            onClick={() => {
              setActivePage((p) => Math.min(1426, p + 1));
              window.scrollTo({ top: 350, behavior: 'smooth' });
            }}
            className="w-9 h-9 rounded-full text-slate-700 hover:bg-slate-100 flex items-center justify-center transition-colors cursor-pointer"
            aria-label="Página siguiente"
          >
            <ChevronRight className="w-5 h-5" />
          </button>
        </nav>
      </div>

      {/* 2. Popular Chilean Comunas */}
      <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-xs">
        <h3 className="text-xs font-bold text-slate-900 uppercase tracking-wider mb-3 flex items-center gap-2">
          <MapPin className="w-4 h-4 text-[#0d9488]" />
          <span>Casas en venta por comuna en Chile</span>
        </h3>
        <div className="flex flex-wrap gap-2">
          {CHILE_COMMUNES.map((comuna) => (
            <button
              key={comuna}
              onClick={() => onSelectCommune(comuna)}
              className="px-3 py-1.5 rounded-lg border border-slate-200 bg-slate-50 hover:bg-teal-50 hover:border-teal-200 text-xs font-medium text-slate-700 hover:text-[#0d9488] transition-colors cursor-pointer"
            >
              Casas en {comuna}
            </button>
          ))}
        </div>
      </div>

      {/* 3. Editorial Guide Section for HogarYa */}
      <div className="bg-white rounded-2xl border border-slate-200 p-6 sm:p-8 shadow-xs">
        <h2 className="text-lg sm:text-xl font-black text-slate-900 tracking-tight mb-4 uppercase">
          VENTA DE CASAS EN CHILE: ENCUENTRA TU PRÓXIMO HOGAR EN HOGARYA
        </h2>
        
        <div className={`space-y-3 text-xs sm:text-sm text-slate-600 leading-relaxed ${!seoExpanded ? 'line-clamp-3' : ''}`}>
          <p>
            En <strong>HogarYa</strong> te acompañamos en una de las decisiones más trascendentales de tu vida: la compra de tu casa propia. Contamos con una cuidada selección de casas en venta en Santiago, Valparaíso, Biobío y las principales regiones del país, con precios transparentes en UF y pesos chilenos actualizados día a día.
          </p>
          <p>
            Tanto si buscas una casa familiar de 3 dormitorios y 2 baños en microbarrios consolidados de La Florida o Maipú, como si sueñas con una parcela campestre en Chicureo o una residencia en Las Condes y Concón, en HogarYa encontrarás información verificada, planos, tours 360° y contacto directo con las mejores corredoras e inmobiliarias.
          </p>
          <p>
            Además, te facilitamos la simulación inmediata de tu dividendo bancario con tasa preferencial, asesoría para la postulación a subsidios habitacionales del MINVU (DS19 y DS1) y acceso al beneficio estatal FOGAES con pie desde el 10%.
          </p>
          <p className="italic text-slate-700 bg-slate-50 p-3.5 rounded-xl border border-slate-200">
            "En HogarYa creemos que cada familia chilena merece acceder a información clara, honesta y sin rodeos. Cotiza con tranquilidad, compara alternativas lado a lado y da el paso hacia tu nuevo hogar."
          </p>
        </div>

        <button
          type="button"
          onClick={() => setSeoExpanded(!seoExpanded)}
          className="mt-3 text-xs font-bold text-[#0d9488] hover:underline flex items-center gap-1 cursor-pointer"
        >
          <span>{seoExpanded ? 'Ver menos' : 'Leer guía completa para comprar casa en Chile'}</span>
          {seoExpanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
        </button>
      </div>

      {/* 4. Mortgage Simulator Banner */}
      <div className="bg-gradient-to-r from-[#0f172a] via-[#1e293b] to-[#0f172a] rounded-2xl p-6 sm:p-8 text-white shadow-lg flex flex-col md:flex-row items-center justify-between gap-6 border border-slate-800">
        <div className="space-y-2 max-w-xl">
          <span className="text-[11px] font-extrabold uppercase tracking-widest text-[#14b8a6]">
            Simulador de Crédito Hipotecario HogarYa
          </span>
          <h3 className="text-xl sm:text-2xl font-black tracking-tight leading-tight">
            Calcula tu dividendo mensual y gestiona tu pre-aprobación bancaria
          </h3>
          <p className="text-xs sm:text-sm text-slate-300">
            Conoce los requisitos de renta, compara tasas de interés y proyecta tus gastos operacionales (CBR, tasación y títulos) al instante.
          </p>
        </div>
        <button
          type="button"
          onClick={onOpenMortgageSimulator}
          className="shrink-0 px-6 py-3.5 bg-[#0d9488] hover:bg-[#0f766e] text-white font-extrabold text-xs sm:text-sm rounded-xl shadow-md transition-all active:scale-95 cursor-pointer"
        >
          Simular Crédito Ahora
        </button>
      </div>

      {/* 5. HogarYa Authentic Footer */}
      <footer className="pt-8 pb-14 border-t border-slate-200 text-xs text-slate-500 space-y-8">
        
        <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4 pb-6 border-b border-slate-200">
          {/* Logo HogarYa */}
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-xl bg-[#0f172a] flex items-center justify-center text-[#14b8a6]">
              <Home className="w-4 h-4" />
            </div>
            <span className="text-xl font-black text-slate-900 tracking-tight">
              Hogar<span className="text-[#0d9488]">Ya</span>
            </span>
          </div>

          <div className="flex flex-wrap items-center gap-4 text-xs font-semibold text-slate-700">
            <span>Redes oficiales</span>
            <div className="flex items-center gap-2">
              {['Instagram', 'LinkedIn', 'Facebook', 'YouTube'].map((net) => (
                <span key={net} className="px-2.5 py-1 bg-slate-100 rounded-md text-[11px] font-bold text-slate-600 hover:text-[#0d9488] cursor-pointer">
                  {net}
                </span>
              ))}
            </div>
            <a href="mailto:contacto@hogarya.cl" className="text-[#0d9488] hover:underline flex items-center gap-1">
              <Mail className="w-3.5 h-3.5" />
              <span>contacto@hogarya.cl</span>
            </a>
          </div>
        </div>

        {/* Footer Link Columns */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-6">
          <div>
            <h4 className="font-bold text-slate-900 mb-2.5 text-xs">Comprar Vivienda</h4>
            <ul className="space-y-1.5 text-slate-600 text-[11px]">
              <li><a href="#casas" className="hover:text-[#0d9488]">Casas en venta Santiago</a></li>
              <li><a href="#deptos" className="hover:text-[#0d9488]">Departamentos en venta</a></li>
              <li><a href="#proyectos" className="hover:text-[#0d9488]">Proyectos entrega inmediata</a></li>
              <li><a href="#terrenos" className="hover:text-[#0d9488]">Terrenos y Parcelas</a></li>
            </ul>
          </div>

          <div>
            <h4 className="font-bold text-slate-900 mb-2.5 text-xs">Arriendos</h4>
            <ul className="space-y-1.5 text-slate-600 text-[11px]">
              <li><a href="#arriendo-casas" className="hover:text-[#0d9488]">Casas en arriendo</a></li>
              <li><a href="#arriendo-deptos" className="hover:text-[#0d9488]">Departamentos en arriendo</a></li>
              <li><a href="#oficinas" className="hover:text-[#0d9488]">Oficinas y Locales</a></li>
            </ul>
          </div>

          <div>
            <h4 className="font-bold text-slate-900 mb-2.5 text-xs">Servicios HogarYa</h4>
            <ul className="space-y-1.5 text-slate-600 text-[11px]">
              <li><button onClick={onOpenMortgageSimulator} className="hover:text-[#0d9488] text-left cursor-pointer">Crédito Hipotecario</button></li>
              <li><a href="#tasaciones" className="hover:text-[#0d9488]">Tasador Online</a></li>
              <li><a href="#subsidios" className="hover:text-[#0d9488]">Guía de Subsidios Minvu</a></li>
              <li><a href="#inversionista" className="hover:text-[#0d9488]">Inversionistas</a></li>
            </ul>
          </div>

          <div>
            <h4 className="font-bold text-slate-900 mb-2.5 text-xs">Transparencia & Seguridad</h4>
            <div className="space-y-2 text-[11px]">
              <div className="p-2.5 bg-slate-50 border border-slate-200 rounded-lg flex items-center gap-2">
                <ShieldCheck className="w-5 h-5 text-emerald-600 shrink-0" />
                <span className="font-bold text-slate-700">Sello de Confianza eCommerce CCS</span>
              </div>
              <p className="text-[10px] text-slate-400">
                HogarYa Chile SpA cumple con los estándares de protección al consumidor y transparencia financiera.
              </p>
            </div>
          </div>
        </div>

        {/* Bottom copyright */}
        <div className="pt-4 border-t border-slate-100 flex flex-col sm:flex-row items-center justify-between gap-2 text-[11px] text-slate-400">
          <p>HogarYa SpA. © 2026 - Todos los derechos reservados. Santiago de Chile.</p>
          <div className="flex items-center gap-3">
            <a href="#terminos" className="hover:underline">Términos y condiciones</a>
            <a href="#privacidad" className="hover:underline">Políticas de privacidad</a>
          </div>
        </div>

      </footer>

    </div>
  );
};
