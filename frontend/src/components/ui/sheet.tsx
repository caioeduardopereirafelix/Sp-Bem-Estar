import * as React from 'react';
import * as DialogPrimitive from '@radix-ui/react-dialog';
import {X} from 'lucide-react';
import {cn} from '@/lib/utils';

export const Sheet = DialogPrimitive.Root;
export const SheetTitle = DialogPrimitive.Title;
export const SheetDescription = DialogPrimitive.Description;
export const SheetClose = DialogPrimitive.Close;

export const SheetContent = React.forwardRef<React.ElementRef<typeof DialogPrimitive.Content>,
    React.ComponentPropsWithoutRef<typeof DialogPrimitive.Content>>(({className, children, ...props}, ref) => (
    <DialogPrimitive.Portal>
        <DialogPrimitive.Content ref={ref} className={cn(
            'fixed z-[901] flex flex-col bg-card text-card-foreground shadow-2xl outline-none',
            'inset-x-0 bottom-0 max-h-[88dvh] rounded-t-xl md:inset-y-0 md:left-auto md:right-0 md:max-h-none md:w-[420px] md:border-l md:rounded-none',
            className)} {...props}>
            {children}
            <DialogPrimitive.Close className="absolute right-3 top-3 inline-flex size-11 items-center justify-center rounded-md text-white hover:bg-white/15 cursor-pointer focus-visible:outline-white focus-visible:-outline-offset-4">
                <X className="size-5" aria-hidden/>
                <span className="sr-only">Fechar</span>
            </DialogPrimitive.Close>
        </DialogPrimitive.Content>
    </DialogPrimitive.Portal>
));
SheetContent.displayName = DialogPrimitive.Content.displayName;
