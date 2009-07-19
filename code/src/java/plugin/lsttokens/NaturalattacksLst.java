/*
 * Copyright 2008 (C) Thomas Parker <thpr@users.sourceforge.net>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package plugin.lsttokens;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;

import pcgen.base.lang.StringUtil;
import pcgen.cdom.base.CDOMObject;
import pcgen.cdom.base.Constants;
import pcgen.cdom.enumeration.FormulaKey;
import pcgen.cdom.enumeration.IntegerKey;
import pcgen.cdom.enumeration.ListKey;
import pcgen.cdom.enumeration.ObjectKey;
import pcgen.cdom.enumeration.StringKey;
import pcgen.cdom.enumeration.Type;
import pcgen.cdom.inst.EquipmentHead;
import pcgen.cdom.reference.CDOMSingleRef;
import pcgen.core.Equipment;
import pcgen.core.Globals;
import pcgen.core.SizeAdjustment;
import pcgen.core.WeaponProf;
import pcgen.core.bonus.Bonus;
import pcgen.core.bonus.BonusObj;
import pcgen.rules.context.Changes;
import pcgen.rules.context.LoadContext;
import pcgen.rules.persistence.token.AbstractToken;
import pcgen.rules.persistence.token.CDOMPrimaryToken;
import pcgen.rules.persistence.token.DeferredToken;
import pcgen.util.Logging;

/**
 * @author djones4
 * 
 */
public class NaturalattacksLst extends AbstractToken implements
		CDOMPrimaryToken<CDOMObject>, DeferredToken<CDOMObject>
{

	private static final Class<WeaponProf> WEAPONPROF_CLASS = WeaponProf.class;

	/**
	 * @see pcgen.persistence.lst.LstToken#getTokenName()
	 */
	@Override
	public String getTokenName()
	{
		return "NATURALATTACKS"; //$NON-NLS-1$
	}

	/**
	 * NATURAL WEAPONS CODE <p/>first natural weapon is primary, the rest are
	 * secondary; NATURALATTACKS:primary weapon name,weapon type,num
	 * attacks,damage|secondary1 weapon name,weapon type,num
	 * attacks,damage|secondary2 format is exactly as it would be in an
	 * equipment lst file Type is of the format Weapon.Natural.Melee.Bludgeoning
	 * number of attacks is the number of attacks with that weapon at BAB (for
	 * primary), or BAB - 5 (for secondary)
	 */
	public boolean parse(LoadContext context, CDOMObject obj, String value)
	{
		if (isEmpty(value) || hasIllegalSeparator('|', value))
		{
			return false;
		}
		// Currently, this isn't going to work with monk attacks
		// - their unarmed stuff won't be affected.

		/*
		 * This does not immediately resolve the Size, because it is an order of
		 * operations issue. This token must allow the SIZE token to appear
		 * AFTER this token in the LST file. Thus a deferred resolution (using a
		 * Resolver) is required.
		 */

		int count = 1;
		StringTokenizer attackTok = new StringTokenizer(value, Constants.PIPE);

		// This is wrong as we need to replace old natural weapons
		// with "better" ones

		while (attackTok.hasMoreTokens())
		{
			String tokString = attackTok.nextToken();
			if (hasIllegalSeparator(',', tokString))
			{
				return false;
			}
			Equipment anEquip = createNaturalWeapon(context, obj, tokString);

			if (anEquip == null)
			{
				Logging.log(Logging.LST_ERROR, "Natural Weapon Creation Failed for : "
						+ tokString);
				return false;
			}

			if (count == 1)
			{
				anEquip.setModifiedName("Natural/Primary");
			}
			else
			{
				anEquip.setModifiedName("Natural/Secondary");
			}

			anEquip.setOutputIndex(0);
			anEquip.setOutputSubindex(count);
			// these values need to be locked.
			anEquip.setQty(new Float(1));
			anEquip.setNumberCarried(new Float(1));

			context.obj.addToList(obj, ListKey.NATURAL_WEAPON, anEquip);
			count++;
		}
		return true;
	}

	/**
	 * Create the Natural weapon equipment item aTok = primary weapon
	 * name,weapon type,num attacks,damage for Example:
	 * Tentacle,Weapon.Natural.Melee.Slashing,*4,1d6
	 * 
	 * @param aTok
	 * @param size
	 * @return natural weapon
	 */
	private Equipment createNaturalWeapon(LoadContext context, CDOMObject obj,
			String wpn)
	{
		StringTokenizer commaTok = new StringTokenizer(wpn, Constants.COMMA);

		int numTokens = commaTok.countTokens();
		// TODO This is wrong :P
		if (numTokens != 4 && numTokens != 5)
		{
			Logging.errorPrint("Invalid Build of " + "Natural Weapon in "
					+ getTokenName() + ": " + wpn);
			return null;
		}

		String attackName = commaTok.nextToken();

		if (attackName.equalsIgnoreCase(Constants.LST_NONE))
		{
			Logging.errorPrint("Attempt to Build 'None' as a "
					+ "Natural Weapon in " + getTokenName() + ": " + wpn);
			return null;
		}

		Equipment anEquip = new Equipment();
		anEquip.setName(attackName);
		anEquip.put(ObjectKey.PARENT, obj);
		/*
		 * This really can't be raw equipment... It really never needs to be
		 * referred to, but this means that duplicates are never being detected
		 * and resolved... this needs to have a KEY defined, to keep it
		 * unique... hopefully this is good enough :)
		 * 
		 * CONSIDER This really isn't that great, because it's String dependent,
		 * and may not remove identical items... it certainly works, but is ugly
		 */
		// anEquip.setKeyName(obj.getClass().getSimpleName() + ","
		// + obj.getKeyName() + "," + wpn);
		/*
		 * Perhaps the construction above should be through context just to
		 * guarantee uniqueness of the key?? - that's too paranoid
		 */

		EquipmentHead equipHead = anEquip.getEquipmentHead(1);

		String profType = commaTok.nextToken();
		if (hasIllegalSeparator('.', profType))
		{
			return null;
		}
		StringTokenizer dotTok = new StringTokenizer(profType, Constants.DOT);
		while (dotTok.hasMoreTokens())
		{
			Type type = Type.getConstant(dotTok.nextToken());
			anEquip.addToListFor(ListKey.TYPE, type);
		}

		String numAttacks = commaTok.nextToken();
		boolean attacksFixed = numAttacks.length() > 0
				&& numAttacks.charAt(0) == '*';
		if (attacksFixed)
		{
			numAttacks = numAttacks.substring(1);
		}
		anEquip.put(ObjectKey.ATTACKS_PROGRESS, Boolean.valueOf(!attacksFixed));
		try
		{
			int bonusAttacks = Integer.parseInt(numAttacks) - 1;
			final BonusObj aBonus = Bonus.newBonus("WEAPON|ATTACKS|"
					+ bonusAttacks);

			if (aBonus == null)
			{
				Logging.errorPrint(getTokenName()
						+ " was given invalid number of attacks: "
						+ bonusAttacks);
				return null;
			}
			anEquip.addToListFor(ListKey.BONUS, aBonus);
		}
		catch (NumberFormatException exc)
		{
			Logging.errorPrint("Non-numeric value for number of attacks in "
					+ getTokenName() + ": '" + numAttacks + "'");
			return null;
		}

		equipHead.put(StringKey.DAMAGE, commaTok.nextToken());

		// sage_sam 02 Dec 2002 for Bug #586332
		// allow hands to be required to equip natural weapons
		int handsrequired = 0;
		if (commaTok.hasMoreTokens())
		{
			final String hString = commaTok.nextToken();
			try
			{
				handsrequired = Integer.valueOf(Integer
						.parseInt(hString));
			}
			catch (NumberFormatException exc)
			{
				Logging.errorPrint("Non-numeric value for hands required: '"
						+ hString + "'");
				return null;
			}
		}
		anEquip.put(IntegerKey.SLOTS, handsrequired);

		anEquip.put(ObjectKey.WEIGHT, BigDecimal.ZERO);

		WeaponProf cwp = context.ref.silentlyGetConstructedCDOMObject(
				WEAPONPROF_CLASS, attackName);
		if (cwp == null)
		{
			cwp = context.ref.constructNowIfNecessary(WEAPONPROF_CLASS,
					attackName);
			cwp.addToListFor(ListKey.TYPE, Type.NATURAL);
		}
		CDOMSingleRef<WeaponProf> wp = context.ref.getCDOMReference(
				WEAPONPROF_CLASS, attackName);
		anEquip.put(ObjectKey.WEAPON_PROF, wp);
		anEquip.addToListFor(ListKey.IMPLIED_WEAPONPROF, wp);

		equipHead.put(IntegerKey.CRIT_RANGE, Integer.valueOf(1));
		equipHead.put(IntegerKey.CRIT_MULT, Integer.valueOf(2));

		return anEquip;
	}

	public String[] unparse(LoadContext context, CDOMObject obj)
	{
		Changes<Equipment> changes = context.getObjectContext().getListChanges(
				obj, ListKey.NATURAL_WEAPON);
		Collection<Equipment> eqadded = changes.getAdded();
		if (eqadded == null || eqadded.isEmpty())
		{
			return null;
		}
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (Equipment lstw : eqadded)
		{
			if (!first)
			{
				sb.append(Constants.PIPE);
			}
			Equipment eq = Equipment.class.cast(lstw);
			String name = eq.getDisplayName();
			// TODO objcontext.getString(eq, StringKey.NAME);
			if (name == null)
			{
				context.addWriteMessage(getTokenName()
						+ " expected Equipment to have a name");
				return null;
			}
			sb.append(name).append(Constants.COMMA);
			List<Type> type = eq.getListFor(ListKey.TYPE);
			if (type == null || type.isEmpty())
			{
				context.addWriteMessage(getTokenName()
						+ " expected Equipment to have a type");
				return null;
			}
			sb.append(StringUtil.join(type, Constants.DOT));
			sb.append(Constants.COMMA);
			Boolean attProgress = eq.get(ObjectKey.ATTACKS_PROGRESS);
			if (attProgress == null)
			{
				context.addWriteMessage(getTokenName()
						+ " expected Equipment to know ATTACKS_PROGRESS state");
				return null;
			}
			else if (!attProgress.booleanValue())
			{
				sb.append(Constants.CHAR_ASTERISK);
			}
			List<BonusObj> bonuses = eq.getListFor(ListKey.BONUS);
			if (bonuses == null || bonuses.isEmpty())
			{
				sb.append("1");
			}
			else
			{
				if (bonuses.size() != 1)
				{
					context.addWriteMessage(getTokenName()
							+ " expected only one BONUS on Equipment: "
							+ bonuses);
					return null;
				}
				// TODO Validate BONUS type?
				BonusObj extraAttacks = bonuses.iterator().next();
				sb.append(Integer.parseInt(extraAttacks.getValue()) + 1);
			}
			sb.append(Constants.COMMA);
			EquipmentHead head = eq.getEquipmentHeadReference(1);
			if (head == null)
			{
				context.addWriteMessage(getTokenName()
						+ " expected an EquipmentHead on Equipment");
				return null;
			}
			String damage = head.get(StringKey.DAMAGE);
			if (damage == null)
			{
				context.addWriteMessage(getTokenName()
						+ " expected a Damage on EquipmentHead");
				return null;
			}
			sb.append(damage);

			Integer hands = eq.get(IntegerKey.SLOTS);
			if (hands != null && hands != 0)
			{
				sb.append(',').append(hands);
			}

			first = false;
		}
		return new String[] { sb.toString() };
	}

	public Class<CDOMObject> getTokenClass()
	{
		return CDOMObject.class;
	}

	public boolean process(LoadContext context, CDOMObject obj)
	{
		List<Equipment> natWeapons = obj.getListFor(ListKey.NATURAL_WEAPON);
		if (natWeapons != null)
		{
			try
			{
				int isize = obj.getSafe(FormulaKey.SIZE).resolve(null, "")
						.intValue();
				SizeAdjustment size = Globals.getContext().ref.getItemInOrder(
						SizeAdjustment.class, isize);
				for (Equipment e : natWeapons)
				{
					e.put(ObjectKey.BASESIZE, size);
					e.put(ObjectKey.SIZE, size);
				}
			}
			catch (NullPointerException npe)
			{
				Logging.errorPrint("SIZE in " + obj.getClass().getSimpleName()
						+ " " + obj.getKeyName() + " must not be a variable "
						+ "if it contains a NATURALATTACKS token");
			}
		}
		return true;
	}

	public Class<CDOMObject> getDeferredTokenClass()
	{
		return getTokenClass();
	}

}
