import React from 'react';
import { Property } from '../types/property';
import { 
  formatUF, 
  formatCLP, 
  ufToClp, 
  calculatePricePerM2Uf, 
  calculateMortgageDividend 
} from '../utils/realEstateCalculations';
import { X, Layers, Check, Phone, MessageCircle } from 'lucide-react';

interface CompareModalProps {
  isOpen: boolean;
  onClose: () => void;
  properties: Property[];
  onRemoveProperty: (id: string) => void;
  onContactProperty: (property: Property) => void;
  onWhatsAppProperty: (property: Property) => void;
}

export const CompareModal: React.FC<CompareModalProps> = ({
  isOpen,
  onClose,
  properties,
  onRemoveProperty,
  onContactProperty,
  onWhatsAppProperty
}) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop */}
      <div 
        className="fixed inset-0 bg-slate-900/60 backdrop-blur-xs transition-opacity"
        onClick={onClose}
      />

      {/* Modal Dialog */}
      <div className="relative w-full max-w-5xl bg-white rounded-3xl shadow-2xl overflow-hidden flex flex-col max-h-[90vh] z-10 animate-in zoom-in-95 duration-200">
        
        {/* Header */}
        <div className="p-5 border-b border-slate-200 flex items-center justify-between bg-slate-50">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-[#e8f5f4] text-[#0d5c56] flex items-center justify-center">
              <Layers className="w-4 h-4" />
            </div>
            <div>
              <h3 className="text-base font-bold text-slate-900">
                Comparador de Casas ({properties.length} seleccionadas)
              </h3>
              <p className="text-xs text-slate-500">
                Revisa las diferencias clave de precio, dividendo y metros cuadrados
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-2 text-slate-400 hover:text-slate-600 rounded-full hover:bg-slate-200/60 transition-colors"
            aria-label="Cerrar comparador"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Comparison Table */}
        <div className="p-6 overflow-x-auto flex-1">
          {properties.length === 0 ? (
            <div className="text-center py-12 text-slate-500 text-sm">
              No tienes propiedades seleccionadas para comparar.
            </div>
          ) : (
            <table className="w-full text-xs text-left border-collapse">
              <thead>
                <tr className="border-b border-slate-200">
                  <th className="p-3 w-44 font-semibold text-slate-400 uppercase text-[11px] bg-slate-50/50">
                    Característica
                  </th>
                  {properties.map((prop) => (
                    <th key={prop.id} className="p-3 min-w-[220px] align-top">
                      <div className="relative group rounded-xl overflow-hidden mb-2 aspect-[16/10] bg-slate-100">
                        {prop.images.length > 0 ? (
                          <img src={prop.images[0]} alt={prop.title} className="w-full h-full object-cover" />
                        ) : (
                          <div className="w-full h-full flex items-center justify-center text-slate-400">Sin foto</div>
                        )}
                        <button
                          type="button"
                          onClick={() => onRemoveProperty(prop.id)}
                          className="absolute top-1.5 right-1.5 p-1 rounded-full bg-black/60 text-white hover:bg-rose-600 transition-colors"
                          title="Quitar"
                        >
                          <X className="w-3.5 h-3.5" />
                        </button>
                      </div>
                      <div className="font-bold text-slate-900 line-clamp-1">{prop.title}</div>
                      <div className="text-[11px] text-slate-500">{prop.comuna}</div>
                    </th>
                  ))}
                </tr>
              </thead>

              <tbody className="divide-y divide-slate-100">
                
                {/* Precio UF */}
                <tr className="hover:bg-slate-50/70">
                  <td className="p-3 font-semibold text-slate-600 bg-slate-50/50">Precio UF</td>
                  {properties.map((prop) => (
                    <td key={prop.id} className="p-3 font-extrabold text-[#0d5c56] text-sm">
                      {formatUF(prop.priceUf)}
                    </td>
                  ))}
                </tr>

                {/* Precio CLP */}
                <tr className="hover:bg-slate-50/70">
                  <td className="p-3 font-semibold text-slate-600 bg-slate-50/50">Precio CLP aprox.</td>
                  {properties.map((prop) => (
                    <td key={prop.id} className="p-3 font-medium text-slate-700">
                      {formatCLP(ufToClp(prop.priceUf))}
                    </td>
                  ))}
                </tr>

                {/* Precio por m² */}
                <tr className="hover:bg-slate-50/70">
                  <td className="p-3 font-semibold text-slate-600 bg-slate-50/50">Precio / m² útil</td>
                  {properties.map((prop) => (
                    <td key={prop.id} className="p-3 font-semibold text-slate-800">
                      {calculatePricePerM2Uf(prop.priceUf, prop.m2Util)} UF/m²
                    </td>
                  ))}
                </tr>

                {/* Dividendo mensual */}
                <tr className="hover:bg-slate-50/70 bg-[#e8f5f4]/30">
                  <td className="p-3 font-semibold text-[#0d5c56] bg-slate-50/50">Dividendo estimado (20% pie)</td>
                  {properties.map((prop) => {
                    const m = calculateMortgageDividend(prop.priceUf);
                    return (
                      <td key={prop.id} className="p-3 font-bold text-[#0d5c56]">
                        {formatCLP(m.dividendClp)}/mes
                      </td>
                    );
                  })}
                </tr>

                {/* Dormitorios */}
                <tr className="hover:bg-slate-50/70">
                  <td className="p-3 font-semibold text-slate-600 bg-slate-50/50">Dormitorios</td>
                  {properties.map((prop) => (
                    <td key={prop.id} className="p-3 font-medium text-slate-800">
                      {prop.dormitorios} dorms
                    </td>
                  ))}
                </tr>

                {/* Baños */}
                <tr className="hover:bg-slate-50/70">
                  <td className="p-3 font-semibold text-slate-600 bg-slate-50/50">Baños</td>
                  {properties.map((prop) => (
                    <td key={prop.id} className="p-3 font-medium text-slate-800">
                      {prop.banos} baños
                    </td>
                  ))}
                </tr>

                {/* Superficie Útil */}
                <tr className="hover:bg-slate-50/70">
                  <td className="p-3 font-semibold text-slate-600 bg-slate-50/50">Superficie útil</td>
                  {properties.map((prop) => (
                    <td key={prop.id} className="p-3 font-medium text-slate-800">
                      {prop.m2Util} m²
                    </td>
                  ))}
                </tr>

                {/* Superficie Total */}
                <tr className="hover:bg-slate-50/70">
                  <td className="p-3 font-semibold text-slate-600 bg-slate-50/50">Terreno total</td>
                  {properties.map((prop) => (
                    <td key={prop.id} className="p-3 font-medium text-slate-800">
                      {prop.m2Total} m²
                    </td>
                  ))}
                </tr>

                {/* Estacionamientos */}
                <tr className="hover:bg-slate-50/70">
                  <td className="p-3 font-semibold text-slate-600 bg-slate-50/50">Estacionamientos</td>
                  {properties.map((prop) => (
                    <td key={prop.id} className="p-3 font-medium text-slate-800">
                      {prop.estacionamientos}
                    </td>
                  ))}
                </tr>

                {/* Estado */}
                <tr className="hover:bg-slate-50/70">
                  <td className="p-3 font-semibold text-slate-600 bg-slate-50/50">Estado</td>
                  {properties.map((prop) => (
                    <td key={prop.id} className="p-3 capitalize font-medium text-slate-800">
                      {prop.status}
                    </td>
                  ))}
                </tr>

                {/* Corredora */}
                <tr className="hover:bg-slate-50/70">
                  <td className="p-3 font-semibold text-slate-600 bg-slate-50/50">Publicado por</td>
                  {properties.map((prop) => (
                    <td key={prop.id} className="p-3 font-medium text-slate-700">
                      {prop.seller.name}
                    </td>
                  ))}
                </tr>

                {/* Contact CTAs */}
                <tr>
                  <td className="p-3 bg-slate-50/50"></td>
                  {properties.map((prop) => (
                    <td key={prop.id} className="p-3">
                      <div className="flex flex-col gap-1.5">
                        <button
                          type="button"
                          onClick={() => {
                            onClose();
                            onContactProperty(prop);
                          }}
                          className="w-full py-2 bg-[#e35238] hover:bg-[#c84128] text-white text-xs font-bold rounded-lg shadow-sm text-center"
                        >
                          Contactar
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            onClose();
                            onWhatsAppProperty(prop);
                          }}
                          className="w-full py-1.5 border border-emerald-600 text-emerald-700 hover:bg-emerald-50 text-xs font-semibold rounded-lg text-center"
                        >
                          WhatsApp
                        </button>
                      </div>
                    </td>
                  ))}
                </tr>

              </tbody>
            </table>
          )}
        </div>

      </div>
    </div>
  );
};
