import * as React from 'react';
import * as TabsPrimitive from '@radix-ui/react-tabs';
import {cn} from '@/lib/utils';

export const Tabs = TabsPrimitive.Root;

export const TabsList = React.forwardRef<React.ElementRef<typeof TabsPrimitive.List>,
    React.ComponentPropsWithoutRef<typeof TabsPrimitive.List>>(({className, ...props}, ref) => (
    <TabsPrimitive.List ref={ref} className={cn('flex border-b border-border', className)} {...props}/>
));
TabsList.displayName = TabsPrimitive.List.displayName;

// Aba ativa marcada por um traço grosso embaixo, como faixa de pista — não só pela cor
export const TabsTrigger = React.forwardRef<React.ElementRef<typeof TabsPrimitive.Trigger>,
    React.ComponentPropsWithoutRef<typeof TabsPrimitive.Trigger>>(({className, ...props}, ref) => (
    <TabsPrimitive.Trigger ref={ref} className={cn(
        'relative flex-1 min-h-12 px-3 text-sm font-semibold text-muted-foreground cursor-pointer',
        'hover:text-foreground data-[state=active]:text-foreground',
        'after:absolute after:inset-x-3 after:-bottom-px after:h-[3px] after:rounded-full after:bg-transparent',
        'data-[state=active]:after:bg-placa',
        className)} {...props}/>
));
TabsTrigger.displayName = TabsPrimitive.Trigger.displayName;

export const TabsContent = React.forwardRef<React.ElementRef<typeof TabsPrimitive.Content>,
    React.ComponentPropsWithoutRef<typeof TabsPrimitive.Content>>(({className, ...props}, ref) => (
    <TabsPrimitive.Content ref={ref} className={cn('flex-1 min-h-0', className)} {...props}/>
));
TabsContent.displayName = TabsPrimitive.Content.displayName;
