import React, { useState } from 'react';
import { 
  Building2, 
  Heart, 
  Menu, 
  X, 
  PlusCircle, 
  User, 
  Layers, 
  Compass, 
  HelpCircle,
  ChevronDown,
  Coins,
  Home,
  Key,
  ShieldCheck,
  Building
} from 'lucide-react';

interface HeaderProps {
  favoritesCount: number;
  compareCount: number;
  onOpenCompare: () => void;
  priceDisplayMode: 'uf' | 'clp';
  onTogglePriceDisplayMode: () => void;
  onOpenSavedAlerts?: () => void;
  onOpenMortgageSimulator?: () => void;
  onSelectOperation?: (op: 'comprar' | 'arrendar') => void;
  onSelectPropertyType?: (type: string) => void;
  isMobileSimulated?: boolean;
}

export const Header: React.FC<HeaderProps> = ({
  favoritesCount,
  compareCount,
  onOpenCompare,
  priceDisplayMode,
  onTogglePriceDisplayMode,
  onOpenSavedAlerts,
  onOpenMortgageSimulator,
  onSelectOperation,
  onSelectPropertyType,
  isMobileSimulated = false
}) => {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [activeTopTab, setActiveTopTab] = useState<'personas' | 'empresas'>('personas');
  const [comprarDropdownOpen, setComprarDropdownOpen] = useState(false);
  const [arrendarDropdownOpen, setArrendarDropdownOpen] = useState(false);

  return (
    <header className="sticky top-0 z-40 bg-white border-b border-slate-200 transition-all">
      
      {/* 1. HogarYa Top Bar */}
      <div className="max-w-[1440px] mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-14 sm:h-16">
          
          {/* Logo & Persona / Empresa Tabs */}
          <div className="flex items-center gap-6 sm:gap-8">
            {/* HogarYa Authentic Brand Logo */}
            <a 
              href="#" 
              className="flex items-center gap-2.5 focus:outline-none focus:ring-2 focus:ring-[#0d9488] rounded-xl p-1 group"
              aria-label="HogarYa - Casas en venta en Chile"
            >
              <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-[#0f172a] to-[#1e293b] flex items-center justify-center text-[#14b8a6] shadow-sm group-hover:scale-105 transition-transform">
                <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
                  <polyline points="9 22 9 12 15 12 15 22" />
                </svg>
              </div>
              <div className="flex flex-col">
                <span className="text-xl font-black tracking-tight text-slate-900 leading-none flex items-center gap-1">
                  Hogar<span className="text-[#0d9488]">Ya</span>
                  <span className="text-[10px] font-bold uppercase tracking-wider bg-teal-50 text-[#0d9488] px-1.5 py-0.2 rounded border border-teal-200/60">
                    Chile
                  </span>
                </span>
                <span className="text-[10px] text-slate-400 font-medium tracking-tight">
                  Portal Inmobiliario
                </span>
              </div>
            </a>

            {/* Personas / Empresas Tabs */}
            {!isMobileSimulated && (
              <div className="hidden md:flex items-center space-x-1 h-14 self-stretch pt-2">
                <button
                  type="button"
                  onClick={() => setActiveTopTab('personas')}
                  className={`h-full px-4 text-xs font-bold transition-colors relative flex items-center ${
                    activeTopTab === 'personas'
                      ? 'text-[#0f172a]'
                      : 'text-slate-500 hover:text-slate-900'
                  }`}
                >
                  Personas
                  {activeTopTab === 'personas' && (
                    <span className="absolute bottom-0 inset-x-2 h-0.5 bg-[#0d9488] rounded-t-md" />
                  )}
                </button>
                <button
                  type="button"
                  onClick={() => setActiveTopTab('empresas')}
                  className={`h-full px-4 text-xs font-bold transition-colors relative flex items-center ${
                    activeTopTab === 'empresas'
                      ? 'text-[#0f172a]'
                      : 'text-slate-500 hover:text-slate-900'
                  }`}
                >
                  Empresas e Inmobiliarias
                  {activeTopTab === 'empresas' && (
                    <span className="absolute bottom-0 inset-x-2 h-0.5 bg-[#0d9488] rounded-t-md" />
                  )}
                </button>
              </div>
            )}
          </div>

          {/* Right Action Controls */}
          <div className="flex items-center gap-2 sm:gap-4">
            
            {/* UF / CLP Header Toggle */}
            <button
              onClick={onTogglePriceDisplayMode}
              className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border border-slate-200 bg-slate-50 hover:bg-slate-100 text-slate-700 text-xs font-semibold transition-colors shadow-2xs"
              title="Alternar moneda principal en toda la página"
            >
              <Coins className="w-3.5 h-3.5 text-[#0d9488]" />
              <span className="hidden sm:inline text-slate-500">Ver en:</span>
              <span className={`px-1.5 py-0.5 rounded text-[11px] font-bold ${priceDisplayMode === 'uf' ? 'bg-[#0f172a] text-white' : 'text-slate-600'}`}>
                UF
              </span>
              <span className={`px-1.5 py-0.5 rounded text-[11px] font-bold ${priceDisplayMode === 'clp' ? 'bg-[#0d9488] text-white' : 'text-slate-600'}`}>
                CLP
              </span>
            </button>

            {/* Compare Drawer Indicator Button */}
            {compareCount > 0 && (
              <button
                onClick={onOpenCompare}
                className="relative flex items-center gap-1.5 px-3 py-1.5 bg-teal-50 text-[#0d9488] border border-teal-200 rounded-lg text-xs font-bold hover:bg-teal-100 transition-all"
                aria-label={`Comparar ${compareCount} propiedades`}
              >
                <Layers className="w-4 h-4" />
                <span className="hidden md:inline">Comparar</span>
                <span className="w-4 h-4 rounded-full bg-[#0d9488] text-white text-[10px] flex items-center justify-center font-bold">
                  {compareCount}
                </span>
              </button>
            )}

            {/* Favoritos */}
            <button
              onClick={onOpenSavedAlerts}
              className="relative p-2 rounded-lg text-slate-600 hover:text-rose-600 hover:bg-rose-50 transition-colors"
              title="Mis propiedades favoritas"
            >
              <Heart className={`w-5 h-5 ${favoritesCount > 0 ? 'fill-rose-500 text-rose-500' : ''}`} />
              {favoritesCount > 0 && (
                <span className="absolute top-1 right-1 w-4 h-4 rounded-full bg-rose-500 text-white text-[10px] font-bold flex items-center justify-center">
                  {favoritesCount}
                </span>
              )}
            </button>

            {/* Ayuda link */}
            <a
              href="#ayuda"
              className="hidden lg:flex items-center gap-1 text-xs font-semibold text-slate-700 hover:text-[#0d9488] transition-colors"
            >
              <HelpCircle className="w-4 h-4 text-slate-400" />
              <span>Ayuda</span>
            </a>

            {/* Iniciar sesión */}
            <div className="relative">
              <button
                onClick={() => setUserMenuOpen(!userMenuOpen)}
                className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:text-[#0d9488] hover:bg-slate-50 rounded-lg transition-colors border border-slate-200"
              >
                <User className="w-4 h-4 text-[#0d9488]" />
                <span className="hidden sm:inline">Iniciar sesión</span>
                <ChevronDown className="w-3.5 h-3.5 text-slate-400" />
              </button>

              {userMenuOpen && (
                <div className="absolute right-0 mt-2 w-56 bg-white rounded-xl shadow-2xl border border-slate-200 py-2 z-50 animate-in fade-in slide-in-from-top-2">
                  <div className="px-4 py-2 border-b border-slate-100">
                    <p className="text-xs font-bold text-slate-900">Mi HogarYa</p>
                    <p className="text-[11px] text-slate-500">Gestiona tus búsquedas y alertas</p>
                  </div>
                  <button 
                    onClick={() => { setUserMenuOpen(false); alert('Iniciar sesión con tu cuenta HogarYa o Google.'); }}
                    className="w-full text-left px-4 py-2 text-xs font-medium text-slate-700 hover:bg-slate-50 hover:text-[#0d9488]"
                  >
                    Ingresar con Correo o ClaveÚnica
                  </button>
                  <button 
                    onClick={() => { setUserMenuOpen(false); alert('Registrar cuenta nueva en HogarYa.'); }}
                    className="w-full text-left px-4 py-2 text-xs font-medium text-slate-700 hover:bg-slate-50 hover:text-[#0d9488]"
                  >
                    Registrarme gratis
                  </button>
                  <div className="border-t border-slate-100 my-1"></div>
                  <button 
                    onClick={() => { setUserMenuOpen(false); alert('Acceso al Portal Inmobiliarias y Corredores de HogarYa.'); }}
                    className="w-full text-left px-4 py-2 text-xs font-bold text-[#0d9488] hover:bg-teal-50"
                  >
                    Portal Clientes Inmobiliarios
                  </button>
                </div>
              )}
            </div>

            {/* "Publicar" Button */}
            <button
              onClick={() => alert('¡Publica tu propiedad en HogarYa! El portal con mayor alcance directo a compradores en Chile.')}
              className="hidden sm:flex items-center gap-1.5 px-4 py-2 rounded-full bg-[#0d9488] hover:bg-[#0f766e] active:scale-95 text-white font-bold text-xs shadow-sm transition-all focus:ring-2 focus:ring-[#0d9488] cursor-pointer"
            >
              <PlusCircle className="w-3.5 h-3.5" />
              <span>Publicar</span>
            </button>

            {/* Mobile Hamburger Toggle */}
            <button
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              className={`${isMobileSimulated ? 'flex' : 'xl:hidden'} p-2 rounded-lg text-slate-700 hover:bg-slate-100`}
              aria-label={mobileMenuOpen ? "Cerrar menú" : "Abrir menú de navegación"}
            >
              {mobileMenuOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
            </button>
          </div>
        </div>
      </div>

      {/* 2. Sub-Navbar in Deep Residential Navy */}
      {!isMobileSimulated && (
        <div className="hidden xl:block bg-[#0f172a] text-white">
          <div className="max-w-[1440px] mx-auto px-4 sm:px-6 lg:px-8">
            <nav className="flex items-center gap-1 text-xs font-semibold h-11" aria-label="Navegación de categorías">
              
              {/* Comprar Dropdown */}
              <div 
                className="relative"
                onMouseEnter={() => setComprarDropdownOpen(true)}
                onMouseLeave={() => setComprarDropdownOpen(false)}
              >
                <button
                  type="button"
                  onClick={() => onSelectOperation?.('comprar')}
                  className="flex items-center gap-1 px-3 py-2 text-white hover:text-[#14b8a6] transition-colors rounded"
                >
                  <span>Comprar</span>
                  <ChevronDown className="w-3.5 h-3.5" />
                </button>

                {comprarDropdownOpen && (
                  <div className="absolute top-full left-0 w-52 bg-white text-slate-800 rounded-xl shadow-2xl border border-slate-200 py-2 z-50">
                    <button
                      onClick={() => { onSelectOperation?.('comprar'); onSelectPropertyType?.('casa'); setComprarDropdownOpen(false); }}
                      className="w-full text-left px-4 py-2 text-xs font-bold text-[#0d9488] hover:bg-teal-50 flex items-center justify-between"
                    >
                      <span>Casas en venta</span>
                      <span className="text-[10px] bg-teal-100 text-[#0f766e] px-1.5 py-0.2 rounded font-extrabold">Activo</span>
                    </button>
                    <button
                      onClick={() => { onSelectOperation?.('comprar'); onSelectPropertyType?.('departamento'); setComprarDropdownOpen(false); }}
                      className="w-full text-left px-4 py-2 text-xs text-slate-700 hover:bg-slate-50"
                    >
                      Departamentos en venta
                    </button>
                    <button
                      onClick={() => { onSelectOperation?.('comprar'); onSelectPropertyType?.('terreno'); setComprarDropdownOpen(false); }}
                      className="w-full text-left px-4 py-2 text-xs text-slate-700 hover:bg-slate-50"
                    >
                      Terrenos y Parcelas
                    </button>
                    <button
                      onClick={() => { onSelectOperation?.('comprar'); setComprarDropdownOpen(false); }}
                      className="w-full text-left px-4 py-2 text-xs text-slate-700 hover:bg-slate-50"
                    >
                      Proyectos Inmobiliarios Nuevos
                    </button>
                  </div>
                )}
              </div>

              {/* Arrendar Dropdown */}
              <div 
                className="relative"
                onMouseEnter={() => setArrendarDropdownOpen(true)}
                onMouseLeave={() => setArrendarDropdownOpen(false)}
              >
                <button
                  type="button"
                  onClick={() => onSelectOperation?.('arrendar')}
                  className="flex items-center gap-1 px-3 py-2 text-white/90 hover:text-[#14b8a6] transition-colors rounded"
                >
                  <span>Arrendar</span>
                  <ChevronDown className="w-3.5 h-3.5" />
                </button>

                {arrendarDropdownOpen && (
                  <div className="absolute top-full left-0 w-52 bg-white text-slate-800 rounded-xl shadow-2xl border border-slate-200 py-2 z-50">
                    <button
                      onClick={() => { onSelectOperation?.('arrendar'); onSelectPropertyType?.('departamento'); setArrendarDropdownOpen(false); }}
                      className="w-full text-left px-4 py-2 text-xs text-slate-700 hover:bg-slate-50"
                    >
                      Departamentos en arriendo
                    </button>
                    <button
                      onClick={() => { onSelectOperation?.('arrendar'); onSelectPropertyType?.('casa'); setArrendarDropdownOpen(false); }}
                      className="w-full text-left px-4 py-2 text-xs text-slate-700 hover:bg-slate-50"
                    >
                      Casas en arriendo
                    </button>
                  </div>
                )}
              </div>

              {/* Crédito Hipotecario */}
              <button 
                type="button"
                onClick={onOpenMortgageSimulator}
                className="px-3 py-2 text-white/90 hover:text-[#14b8a6] transition-colors flex items-center gap-1.5"
              >
                <span>Crédito Hipotecario</span>
                <span className="text-[10px] bg-[#0d9488] text-white font-bold px-1.5 py-0.2 rounded-full">
                  Simulador
                </span>
              </button>

              {/* Subsidios */}
              <a 
                href="#subsidios" 
                className="px-3 py-2 text-white/90 hover:text-[#14b8a6] transition-colors"
              >
                Subsidios (DS19 / DS1)
              </a>

              {/* Tasar Propiedades */}
              <button 
                type="button"
                onClick={() => alert('Tasador Online HogarYa: Obtén el valor de mercado estimado de cualquier dirección en Chile en segundos.')}
                className="px-3 py-2 text-white/90 hover:text-[#14b8a6] transition-colors"
              >
                Tasar Propiedades
              </button>

              {/* Inversionista */}
              <button 
                type="button"
                onClick={() => alert('Sección Inversionistas HogarYa: Departamentos con arriendo garantizado y alta rentabilidad UF + 5.5%.')}
                className="px-3 py-2 text-white/90 hover:text-[#14b8a6] transition-colors"
              >
                Inversionista
              </button>

            </nav>
          </div>
        </div>
      )}

      {/* Mobile Drawer */}
      {mobileMenuOpen && (
        <div className={`${isMobileSimulated ? 'block' : 'xl:hidden'} bg-white border-b border-slate-200 px-4 pt-3 pb-6 space-y-3 shadow-xl`}>
          <div className="flex items-center justify-between pb-3 border-b border-slate-100">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-400">
              Menú HogarYa
            </span>
            <button
              onClick={onTogglePriceDisplayMode}
              className="flex items-center gap-1 px-2.5 py-1 bg-slate-100 rounded text-xs font-semibold text-slate-700"
            >
              <Coins className="w-3.5 h-3.5 text-[#0d9488]" />
              <span>Ver en: <strong>{priceDisplayMode.toUpperCase()}</strong></span>
            </button>
          </div>

          <div className="grid grid-cols-2 gap-2">
            <button 
              onClick={() => { onSelectOperation?.('comprar'); setMobileMenuOpen(false); }}
              className="p-2.5 text-sm font-bold text-[#0d9488] bg-teal-50 rounded-lg flex items-center gap-2 justify-center"
            >
              <Home className="w-4 h-4" /> Comprar
            </button>
            <button 
              onClick={() => { onSelectOperation?.('arrendar'); setMobileMenuOpen(false); }}
              className="p-2.5 text-sm font-semibold text-slate-700 bg-slate-50 hover:bg-slate-100 rounded-lg flex items-center gap-2 justify-center"
            >
              <Key className="w-4 h-4" /> Arrendar
            </button>
          </div>

          <div className="space-y-1 pt-1 text-xs">
            <button 
              onClick={() => { setMobileMenuOpen(false); onOpenMortgageSimulator?.(); }}
              className="w-full text-left px-3 py-2 text-slate-700 hover:bg-slate-50 rounded-lg flex items-center justify-between"
            >
              <span className="font-semibold">Simulador Crédito Hipotecario</span>
              <span className="text-[10px] font-bold bg-[#0d9488] text-white px-2 py-0.5 rounded-full">Calcular</span>
            </button>
            <button 
              onClick={() => { setMobileMenuOpen(false); alert('Subsidios Minvu DS19 y DS1 disponibles'); }}
              className="w-full text-left px-3 py-2 text-slate-700 hover:bg-slate-50 rounded-lg"
            >
              Guía de Subsidios Minvu
            </button>
            <button 
              onClick={() => { setMobileMenuOpen(false); alert('Tasador de propiedades HogarYa'); }}
              className="w-full text-left px-3 py-2 text-slate-700 hover:bg-slate-50 rounded-lg"
            >
              Tasar Propiedades Gratis
            </button>
            <button 
              onClick={() => { setMobileMenuOpen(false); alert('Departamentos para inversión'); }}
              className="w-full text-left px-3 py-2 text-slate-700 hover:bg-slate-50 rounded-lg"
            >
              Inversionista
            </button>
          </div>

          <div className="pt-2 border-t border-slate-100">
            <button 
              onClick={() => {
                setMobileMenuOpen(false);
                alert('Publica tu propiedad en HogarYa');
              }}
              className="w-full py-2.5 bg-[#0d9488] text-white font-bold text-sm rounded-lg flex items-center justify-center gap-2 shadow"
            >
              <PlusCircle className="w-4 h-4" /> Publicar Propiedad
            </button>
          </div>
        </div>
      )}

    </header>
  );
};
