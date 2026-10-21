import * as React from 'react';
import {Slot} from '@radix-ui/react-slot';
import {cva, type VariantProps} from 'class-variance-authority';
import {cn} from '@/lib/utils';

const buttonVariants = cva(
    'inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-semibold transition-colors disabled:pointer-events-none disabled:opacity-60 [&_svg]:size-4 [&_svg]:shrink-0 cursor-pointer',
    {
        variants: {
            variant: {
                default: 'bg-primary text-primary-foreground hover:bg-primary/90',
                outline: 'border border-input bg-card hover:bg-accent hover:text-accent-foreground',
                secondary: 'bg-secondary text-secondary-foreground hover:bg-secondary/70',
                ghost: 'hover:bg-accent hover:text-accent-foreground',
                link: 'text-placa-text underline underline-offset-4 hover:no-underline',
            },
            size: {
                default: 'h-11 px-4',
                sm: 'h-9 px-3',
                icon: 'size-11',
            },
        },
        defaultVariants: {variant: 'default', size: 'default'},
    },
);

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement>, VariantProps<typeof buttonVariants> {
    asChild?: boolean;
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
    ({className, variant, size, asChild = false, ...props}, ref) => {
        const Comp = asChild ? Slot : 'button';
        return <Comp ref={ref} className={cn(buttonVariants({variant, size}), className)} {...props}/>;
    },
);
Button.displayName = 'Button';
