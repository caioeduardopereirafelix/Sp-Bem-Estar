import * as React from 'react';
import * as SelectPrimitive from '@radix-ui/react-select';
import {Check, ChevronDown} from 'lucide-react';
import {cn} from '@/lib/utils';

export const Select = SelectPrimitive.Root;
export const SelectValue = SelectPrimitive.Value;

export const SelectTrigger = React.forwardRef<React.ElementRef<typeof SelectPrimitive.Trigger>,
    React.ComponentPropsWithoutRef<typeof SelectPrimitive.Trigger>>(({className, children, ...props}, ref) => (
    <SelectPrimitive.Trigger ref={ref} className={cn(
        'flex h-11 w-full items-center justify-between gap-2 rounded-md border border-input bg-card px-3 text-sm',
        'data-[placeholder]:text-muted-foreground cursor-pointer', className)} {...props}>
        <span className="truncate">{children}</span>
        <SelectPrimitive.Icon asChild><ChevronDown className="size-4 opacity-70" aria-hidden/></SelectPrimitive.Icon>
    </SelectPrimitive.Trigger>
));
SelectTrigger.displayName = SelectPrimitive.Trigger.displayName;

export const SelectContent = React.forwardRef<React.ElementRef<typeof SelectPrimitive.Content>,
    React.ComponentPropsWithoutRef<typeof SelectPrimitive.Content>>(({className, children, position = 'popper', ...props}, ref) => (
    <SelectPrimitive.Portal>
        <SelectPrimitive.Content ref={ref} position={position} sideOffset={4} className={cn(
            'relative z-[1000] max-h-80 min-w-[8rem] overflow-hidden rounded-md border bg-popover text-popover-foreground shadow-lg',
            position === 'popper' && 'w-[var(--radix-select-trigger-width)]', className)} {...props}>
            <SelectPrimitive.Viewport className="p-1">{children}</SelectPrimitive.Viewport>
        </SelectPrimitive.Content>
    </SelectPrimitive.Portal>
));
SelectContent.displayName = SelectPrimitive.Content.displayName;

export const SelectItem = React.forwardRef<React.ElementRef<typeof SelectPrimitive.Item>,
    React.ComponentPropsWithoutRef<typeof SelectPrimitive.Item>>(({className, children, ...props}, ref) => (
    <SelectPrimitive.Item ref={ref} className={cn(
        'relative flex min-h-10 w-full cursor-pointer select-none items-center rounded-sm py-2 pl-8 pr-2 text-sm outline-none',
        'data-[highlighted]:bg-accent data-[highlighted]:text-accent-foreground', className)} {...props}>
        <span className="absolute left-2 flex size-4 items-center justify-center">
            <SelectPrimitive.ItemIndicator><Check className="size-4" aria-hidden/></SelectPrimitive.ItemIndicator>
        </span>
        <SelectPrimitive.ItemText>{children}</SelectPrimitive.ItemText>
    </SelectPrimitive.Item>
));
SelectItem.displayName = SelectPrimitive.Item.displayName;
