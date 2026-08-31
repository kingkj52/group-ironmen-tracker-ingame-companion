package com.groupironmencompanion.data;

/**
 * One entry in a member's item container. A quantity of zero with id zero represents an
 * empty slot in an order-preserving container (inventory, equipment).
 */
public final class ItemStack
{
	private final int id;
	private final int quantity;

	public ItemStack(int id, int quantity)
	{
		this.id = id;
		this.quantity = quantity;
	}

	public int getId()
	{
		return id;
	}

	public int getQuantity()
	{
		return quantity;
	}

	public boolean isEmpty()
	{
		return id <= 0 || quantity <= 0;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof ItemStack))
		{
			return false;
		}
		ItemStack other = (ItemStack) o;
		return other.id == id && other.quantity == quantity;
	}

	@Override
	public int hashCode()
	{
		return id * 31 + quantity;
	}
}
