import * as React from "react"
import { Link } from "react-router"

import { cn } from "@/lib/utils"

/**
 * One row of a list: leading icon/avatar + truncating title/subtitle + value +
 * trailing actions. The seam exists because every feature screen used to
 * reimplement this flex layout by hand, and the min-w-0/truncate/shrink-0
 * combination kept drifting out of sync (the row would squeeze the title to
 * nothing next to a long amount — fixed in 6 places at once before this
 * component existed).
 *
 * The clickable area (leading + title/subtitle + value) and `trailing` are
 * never the same interactive element — trailing (menu buttons, etc.) always
 * sits outside it, so a clickable row never nests a button/link inside
 * another one.
 */
type ListRowBaseProps = {
  leading?: React.ReactNode
  title: React.ReactNode
  subtitle?: React.ReactNode
  value?: React.ReactNode
  trailing?: React.ReactNode
  className?: string
  /** Accessible name for the clickable area, when it needs to differ from its visible text (e.g. to drop the amount from the name). */
  "aria-label"?: string
}

type ListRowProps =
  | (ListRowBaseProps & { href: string; onClick?: never })
  | (ListRowBaseProps & { href?: never; onClick: () => void })
  | (ListRowBaseProps & { href?: never; onClick?: never })

function ListRow(props: ListRowProps) {
  const { leading, title, subtitle, value, trailing, className } = props
  const ariaLabel = props["aria-label"]

  const body = (
    <>
      {leading && (
        <span className="flex shrink-0 items-center justify-center">{leading}</span>
      )}
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="truncate text-[13px] font-bold text-ink-primary">{title}</span>
        {subtitle && (
          <span className="truncate text-[11px] text-ink-tertiary">{subtitle}</span>
        )}
      </div>
      {value !== undefined && (
        <span className="ml-auto shrink-0 tabular-nums text-[14px] font-extrabold text-ink-primary">
          {value}
        </span>
      )}
    </>
  )

  const contentClassName = "flex min-w-0 flex-1 items-center gap-2.5 text-left"

  return (
    <div
      data-slot="list-row"
      className={cn("flex items-center gap-2.5 py-2.5", className)}
    >
      {props.href ? (
        <Link to={props.href} aria-label={ariaLabel} className={contentClassName}>
          {body}
        </Link>
      ) : props.onClick ? (
        <button
          type="button"
          onClick={props.onClick}
          aria-label={ariaLabel}
          className={contentClassName}
        >
          {body}
        </button>
      ) : (
        <div className={contentClassName}>{body}</div>
      )}
      {trailing && <div className="flex shrink-0 items-center gap-1">{trailing}</div>}
    </div>
  )
}

export { ListRow }
