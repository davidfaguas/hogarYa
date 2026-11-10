import React, { useState } from 'react';
import { Property } from '../types/property';
import { 
  formatUF, 
  formatCLP, 
  ufToClp, 
  calculateMortgageDividend,
  CURRENT_UF_VALUE_CLP
} from '../utils/realEstateCalculations';
import { X, Calculator, ShieldCheck, CheckCircle2, Info, ArrowRight } from 'lucide-react';

interface MortgageSimulatorModalProps {
  isOpen: boolean;
  onClose: () => void;
  initialProperty?: Property | null;
}

export const MortgageSimulatorModal: React.FC<MortgageSimulatorModalProps> = ({
  isOpen,
  onClose,
  initialProperty
}) => {
  const [propertyPriceUf, setPropertyPriceUf] = useState<number>(
    initialProperty ? initialProperty.priceUf : 5500
  );
  const [piePercent, setPiePercent] = useState<number>(20);
  const [loanYears, setLoanYears] = useState<number>(25);
  const [annualRate, setAnnualRate] = useState<number>(4.5);

  if (!isOpen) return null;

  const result = calculateMortgageDividend(
    propertyPriceUf,
    piePercent,
    loanYears,
    annualRate,
    CURRENT_UF_VALUE_CLP
  );

  // Chilean Operational Costs Approximation
  const tasacionUf = 2.5;
  const titulosUf = 5.5;
  const cbrClp = Math.round(ufToClp(propertyPriceUf) * 0.002); // ~0.2% CBR
  const timbresUf = 1.8;
  const totalGastosOperacionalesClp = ufToClp(tasacionUf + titulosUf + timbresUf) + cbrClp;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop */}
      <div 
        className="fixed inset-0 bg-slate-900/60 backdrop-blur-xs transition-opacity"
        onClick={onClose}
      />

      {/* Dialog Box */}
      <div className="relative w-full max-w-2xl bg-white rounded-3xl shadow-2xl overflow-hidden flex flex-col max-h-[90vh] z-10 animate-in zoom-in-95 duration-200">
        
        {/* Header */}
        <div className="p-5 border-b border-slate-200 flex items-center justify-between bg-gradient-to-r from-[#0d5c56] to-[#083d39] text-white">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-xl bg-white/10 flex items-center justify-center text-white">
              <Calculator className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold">Simulador de Crédito Hipotecario</h3>
              <p className="text-xs text-emerald-100">Tasas vigentes bancos chilenos 2026</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-2 text-white/80 hover:text-white rounded-full hover:bg-white/10 transition-colors"
            aria-label="Cerrar simulador"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        <div className="p-6 overflow-y-auto space-y-6 text-xs text-slate-700">
          
          {/* Valor de la propiedad */}
          <div>
            <div className="flex items-center justify-between mb-1.5">
              <label className="font-bold text-slate-900 uppercase text-[11px] tracking-wider">
                Valor de la propiedad
              </label>
              <span className="font-extrabold text-sm text-[#0d5c56]">
                {formatUF(propertyPriceUf)} (~{formatCLP(ufToClp(propertyPriceUf))})
              </span>
            </div>
            <input
              type="range"
              min={1500}
              max={25000}
              step={100}
              value={propertyPriceUf}
              onChange={(e) => setPropertyPriceUf(Number(e.target.value))}
              className="w-full accent-[#0d5c56] cursor-pointer"
            />
            <div className="flex justify-between text-[11px] text-slate-400 mt-1">
              <span>1.500 UF</span>
              <span>10.000 UF</span>
              <span>25.000 UF</span>
            </div>
          </div>

          {/* Pie % */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <label className="font-bold text-slate-900 uppercase text-[11px] tracking-wider">
                Pie inicial
              </label>
              <span className="font-bold text-slate-800">
                {piePercent}% = {formatUF(result.pieUf)} (~{formatCLP(result.pieClp)})
              </span>
            </div>
            <div className="grid grid-cols-4 gap-2">
              {[10, 15, 20, 30].map((pct) => (
                <button
                  key={pct}
                  type="button"
                  onClick={() => setPiePercent(pct)}
                  className={`py-2 rounded-xl font-bold border transition-all ${
                    piePercent === pct
                      ? 'bg-[#0d5c56] text-white border-[#0d5c56]'
                      : 'bg-slate-50 text-slate-700 border-slate-200 hover:bg-slate-100'
                  }`}
                >
                  {pct}% {pct === 10 && <span className="block text-[9px] font-normal">Con FOGAES</span>}
                </button>
              ))}
            </div>
          </div>

          {/* Plazo del crédito */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block font-bold text-slate-900 uppercase text-[11px] tracking-wider mb-2">
                Plazo (Años)
              </label>
              <div className="grid grid-cols-3 gap-1.5">
                {[20, 25, 30].map((yrs) => (
                  <button
                    key={yrs}
                    type="button"
                    onClick={() => setLoanYears(yrs)}
                    className={`py-2 rounded-xl font-bold border text-center transition-all ${
                      loanYears === yrs
                        ? 'bg-[#0d5c56] text-white border-[#0d5c56]'
                        : 'bg-slate-50 text-slate-700 border-slate-200 hover:bg-slate-100'
                    }`}
                  >
                    {yrs} años
                  </button>
                ))}
              </div>
            </div>

            <div>
              <div className="flex justify-between items-center mb-1">
                <label className="font-bold text-slate-900 uppercase text-[11px] tracking-wider">
                  Tasa Anual Estimada
                </label>
                <span className="font-bold text-[#0d5c56]">{annualRate}%</span>
              </div>
              <input
                type="range"
                min={3.8}
                max={6.0}
                step={0.1}
                value={annualRate}
                onChange={(e) => setAnnualRate(Number(e.target.value))}
                className="w-full accent-[#0d5c56] mt-2 cursor-pointer"
              />
              <div className="text-[10px] text-slate-400 mt-1">Promedio banca chilena: 4,5% + UF</div>
            </div>
          </div>

          {/* Results Summary Box */}
          <div className="bg-[#e8f5f4] rounded-2xl p-4 sm:p-5 border border-[#0d5c56]/30 space-y-3">
            <div className="text-center">
              <span className="text-xs uppercase tracking-wider font-bold text-[#0d5c56]">
                Dividendo mensual estimado
              </span>
              <div className="text-2xl sm:text-3xl font-extrabold text-[#0d5c56] mt-0.5">
                {formatCLP(result.dividendClp)}
                <span className="text-sm font-semibold text-slate-600 ml-1.5">/ mes</span>
              </div>
              <div className="text-xs font-semibold text-slate-600 mt-0.5">
                Equivalente a {result.dividendUf} UF/mes
              </div>
            </div>

            <div className="border-t border-[#0d5c56]/20 pt-3 grid grid-cols-2 gap-3 text-center sm:text-left">
              <div>
                <span className="text-slate-500 font-medium text-[11px]">Renta líquida sugerida:</span>
                <p className="font-bold text-slate-900 text-sm">{formatCLP(result.minIncomeSuggestedClp)}</p>
                <span className="text-[10px] text-slate-400">(Puede complementarse en pareja)</span>
              </div>
              <div>
                <span className="text-slate-500 font-medium text-[11px]">Monto del crédito hipotecario:</span>
                <p className="font-bold text-slate-900 text-sm">{formatUF(result.loanUf)}</p>
                <span className="text-[10px] text-slate-400">(Financiamiento al {100 - piePercent}%)</span>
              </div>
            </div>
          </div>

          {/* Chilean Operational Costs Breakdown */}
          <div className="border border-slate-200 rounded-2xl p-4 bg-slate-50 space-y-2">
            <div className="font-bold text-slate-900 flex items-center justify-between">
              <span>Gastos operacionales estimados en Chile:</span>
              <span className="text-[#0d5c56] font-bold">~{formatCLP(totalGastosOperacionalesClp)}</span>
            </div>
            <div className="space-y-1 text-[11px] text-slate-600">
              <div className="flex justify-between">
                <span>• Tasación pericial bancaria:</span>
                <span className="font-medium">{formatUF(tasacionUf)} ({formatCLP(ufToClp(tasacionUf))})</span>
              </div>
              <div className="flex justify-between">
                <span>• Estudio de títulos y redacción de escritura:</span>
                <span className="font-medium">{formatUF(titulosUf)} ({formatCLP(ufToClp(titulosUf))})</span>
              </div>
              <div className="flex justify-between">
                <span>• Impuesto al mutuo / timbres y estampillas:</span>
                <span className="font-medium">{formatUF(timbresUf)} ({formatCLP(ufToClp(timbresUf))})</span>
              </div>
              <div className="flex justify-between">
                <span>• Inscripción en Conservador de Bienes Raíces (CBR):</span>
                <span className="font-medium">~{formatCLP(cbrClp)}</span>
              </div>
            </div>
          </div>

          <div className="flex items-center gap-2 text-slate-500 text-[11px] bg-slate-100 p-2.5 rounded-xl">
            <Info className="w-4 h-4 text-slate-600 shrink-0" />
            <span>
              Simulación referencial sujeta a evaluación comercial y de riesgo de la entidad bancaria según la CMF.
            </span>
          </div>

        </div>

        {/* Footer */}
        <div className="p-4 bg-slate-50 border-t border-slate-200 flex items-center justify-end gap-3">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2.5 border border-slate-300 rounded-xl text-xs font-semibold text-slate-700 hover:bg-slate-100"
          >
            Cerrar
          </button>
          <button
            type="button"
            onClick={() => {
              alert('Solicitud enviada. Un ejecutivo bancario del convenio Andes Habitat te contactará para tu preaprobación con tasa preferencial.');
              onClose();
            }}
            className="px-5 py-2.5 bg-[#e35238] hover:bg-[#c84128] text-white rounded-xl text-xs font-bold shadow-md shadow-[#e35238]/20 flex items-center gap-1.5"
          >
            <span>Pedir Preaprobación con Bancos</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </button>
        </div>

      </div>
    </div>
  );
};
