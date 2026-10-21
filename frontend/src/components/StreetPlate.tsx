import type {ReactNode} from 'react';
import {cn} from '@/lib/utils';

/**
 * Placa no formato das placas de rua de São Paulo: azul, filete branco por dentro,
 * nome grande e uma linha menor embaixo — onde a placa real traz o bairro e o CEP.
 */
export function StreetPlate({title, caption, as: Tag = 'div', className}: {
    title: ReactNode;
    caption?: ReactNode;
    as?: 'div' | 'header';
    className?: string;
}) {
    return (
        <Tag className={cn('placa', className)}>
            <div className="placa-inner">
                <div className="text-[1.375rem] font-extrabold leading-[1.1] tracking-[-0.01em]">{title}</div>
                {caption && <div className="mt-1 text-[0.8125rem] font-semibold leading-tight opacity-95">{caption}</div>}
            </div>
        </Tag>
    );
}
