import * as React from 'react';
import * as RadioGroupPrimitive from '@radix-ui/react-radio-group';
import {cn} from '@/lib/utils';

export const RadioGroup = React.forwardRef<React.ElementRef<typeof RadioGroupPrimitive.Root>,
    React.ComponentPropsWithoutRef<typeof RadioGroupPrimitive.Root>>(({className, ...props}, ref) => (
    <RadioGroupPrimitive.Root ref={ref} className={cn('grid gap-2', className)} {...props}/>
));
RadioGroup.displayName = RadioGroupPrimitive.Root.displayName;

export const RadioGroupItem = React.forwardRef<React.ElementRef<typeof RadioGroupPrimitive.Item>,
    React.ComponentPropsWithoutRef<typeof RadioGroupPrimitive.Item>>(({className, ...props}, ref) => (
    <RadioGroupPrimitive.Item ref={ref} className={cn(
        'aspect-square size-5 shrink-0 rounded-full border-2 border-input bg-card cursor-pointer',
        'data-[state=checked]:border-placa', className)} {...props}>
        <RadioGroupPrimitive.Indicator className="flex items-center justify-center">
            <span className="size-2.5 rounded-full bg-placa"/>
        </RadioGroupPrimitive.Indicator>
    </RadioGroupPrimitive.Item>
));
RadioGroupItem.displayName = RadioGroupPrimitive.Item.displayName;
