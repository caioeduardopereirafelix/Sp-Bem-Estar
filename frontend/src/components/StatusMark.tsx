import type {IssueStatus} from '@/lib/api';
import {shapeSvg, STATUS} from '@/lib/status';
import {cn} from '@/lib/utils';

export function StatusShape({status, size = 16}: {status: IssueStatus; size?: number}) {
    return <span className="inline-flex shrink-0" aria-hidden dangerouslySetInnerHTML={{__html: shapeSvg(status, size)}}/>;
}

export function StatusMark({status, className}: {status: IssueStatus; className?: string}) {
    return (
        <span className={cn('inline-flex items-center gap-1.5 font-semibold', className)}>
            <StatusShape status={status}/>
            {STATUS[status].label}
        </span>
    );
}
